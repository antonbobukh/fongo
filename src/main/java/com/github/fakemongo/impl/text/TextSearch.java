package com.github.fakemongo.impl.text;

import com.github.fakemongo.impl.index.IndexAbstract;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.FongoDB;
import com.mongodb.FongoDBCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Emulates Text Search by sending multiple finds with regex
 *
 * Can be used for:
 * db runCommand search:
 * http://docs.mongodb.org/manual/tutorial/search-for-text/
 * $text impl in find query:
 * http://docs.mongodb.org/master/reference/operator/query/text/
 * aggregation text search:
 * http://docs.mongodb.org/master/tutorial/text-search-in-aggregation/
 *
 * Requires a text index on a single field http://docs.mongodb.org/manual/core/index-text/
 *
 * Not quite correct: actually works better (finds more results) than mongo's (2.4.9 - 2.6.rc-1) text Search.
 * Does not support languages and stop words.
 * Does not support weight in indexes (text indexes support is not full) .
 * Scores are calculated not 100% precisely.
 *
 * @author Alexander Arutuniants <alex.art@in2circle.com>
 */
//TODO: Add TextIndexes and Weights Support see: http://docs.mongodb.org/manual/core/index-text/
public class TextSearch {

  private final static Logger LOG = LoggerFactory.getLogger(TextSearch.class);

  private final DBCollection collection;
  private final Set<String> textIndexFields;

  private final Map results = new HashMap<DBObject, Double>();

  private String searchString;
  private DBObject project;
  private int limit;

  private List<String> allWords;
  private List<String> fullPhrasesToSearch;
  private List<String> negatedWordsToSearch;
  private List<String> wordsToSearch;

  public TextSearch(FongoDBCollection collection) {
    this.collection = collection;
    this.textIndexFields = searchTextIndexFields(collection, true);
  }

  private <T> List<T> subtractLists(List<T> list1, List<T> list2) {
    List<T> result = new ArrayList<T>();
    Set<T> set2 = new HashSet<T>(list2);
    for (T t1 : list1) {
      if (!set2.contains(t1)) {
        result.add(t1);
      }
    }
    return result;
  }

  private Set<String> searchTextIndexFields(FongoDBCollection collection, boolean unique) {
    Collection<IndexAbstract> indexes = collection.getIndexes();
    IndexAbstract result = null;
    Set<String> indexFields = new TreeSet<String>();
    for (IndexAbstract index : indexes) {
      DBObject keys = index.getKeys();
      for (String field : (Set<String>) index.getFields()) {
        if (keys.get(field).equals("text")) {
          if (result != null && unique) {
            ((FongoDB) collection.getDB())
                    .notOkErrorResult(-5, "more than one text index, not sure which to run text search on").throwOnError();
          }
          result = index;
          indexFields.add(field);
          if (!unique) {
            break;
          }
        }
      }
    }

    LOG.debug("searchTextIndex() found index {}", result);

    return indexFields;
  }

  private List<String> getWordsByRegex(String string, String regex) {
    List<String> result = new ArrayList();
    Matcher matcherSW
            = Pattern.compile(regex)
            .matcher(searchString);
    while (matcherSW.find()) {
      String matchPhrase = matcherSW.group(1);
      result.add(matchPhrase);
    }
    return result;
  }

  private List<DBObject> findMatchesInCollection(DBCollection collection, List<String> stringsToSearch, DBObject project) {
    Iterator textKeyIterator = textIndexFields.iterator();
    int wordsCount = stringsToSearch.size();
    BasicDBObject findQuery;
    BasicDBList ors = new BasicDBList();
    while (textKeyIterator.hasNext()) {
      String key = (String) textKeyIterator.next();
      for (int i = 0; i < wordsCount; i++) {
        ors.add(new BasicDBObject(key,
                java.util.regex.Pattern.compile("\\b" + stringsToSearch.get(i) + "\\b", Pattern.CASE_INSENSITIVE)));
      }
    }
    findQuery = new BasicDBObject("$or", ors);

    DBCursor negationSearchResultCursor = collection.find(findQuery, project);

    List<DBObject> result = new ArrayList<DBObject>();

    while (negationSearchResultCursor.hasNext()) {
      result.add(negationSearchResultCursor.next());
    }
    return result;
  }

  private BasicDBList sortByScoreAndLimit(Map mapToSotr, int limit) {
    List<Map.Entry> sortedRes = new ArrayList<Map.Entry>(mapToSotr.entrySet());
    Collections.sort(sortedRes,
            new Comparator() {
              @Override
              public int compare(Object o1, Object o2) {
                Map.Entry e1 = (Map.Entry) o1;
                Map.Entry e2 = (Map.Entry) o2;
                return ((Comparable) e2.getValue()).compareTo(e1.getValue());
              }
            });

    BasicDBList res = new BasicDBList();
    int till = 0;
    for (Map.Entry e : sortedRes) {
      res.add(new BasicDBObject("score", e.getValue()).append("obj", e.getKey()));
      till++;
      if (till >= limit) {
        break;
      }
    }
    return res;
  }

  private void buildResultsFromList(List<DBObject> resultsToInclude, List<DBObject> resultsNotToInclude) {

    for (DBObject result : resultsToInclude) {
      Double score;
      if (resultsNotToInclude.contains(result)) {
        continue;
      } else if (results.containsKey(result)) {
        score = (Double) results.get(result) + 1.25;
      } else {
        score = 1.25;
      }
      results.put(result, score);
    }
  }

  private DBObject BuildResponce(BasicDBList results) {
    BasicDBObject res = new BasicDBObject("language", "english");
    res.put("results", results);
    res.put("stats", "it's fake, sorry");
    res.put("ok", 1);

    return res;
  }

  public DBObject findByTextSearch(String searchString, DBObject project) {
    return findByTextSearch(searchString, project, 100);
  }

  public DBObject findByTextSearch(String searchString) {
    return findByTextSearch(searchString, null, 100);
  }

  public DBObject findByTextSearch(String searchString, DBObject project, int limit) {
    this.searchString = searchString;
    this.project = project;
    this.limit = (limit > 100 || limit <= 0) ? 100 : limit;

    //Words Lists
    allWords = getWordsByRegex(searchString, "([[^\\p{Space}\\\\\\\"-]&&\\p{Alnum}&&[^\\p{Space}\\\\\\\"]]+)");
    fullPhrasesToSearch = getWordsByRegex(searchString, "\"\\s*(.*?)\\s*\"");
    negatedWordsToSearch = getWordsByRegex(searchString, "-(.\\S*)\\s*");
    wordsToSearch = subtractLists(allWords, negatedWordsToSearch);

    // Find Negations
    List<DBObject> negatedSearchResults = findMatchesInCollection(collection, negatedWordsToSearch, project);

    //Find Phrases    
    List<DBObject> phrasesSearchResult = findMatchesInCollection(collection, fullPhrasesToSearch, project);

    //Find Words
    List<DBObject> wordsSearchResult = findMatchesInCollection(collection, wordsToSearch, project);

    //Generating results  
    buildResultsFromList(phrasesSearchResult, negatedSearchResults);
    buildResultsFromList(wordsSearchResult, negatedSearchResults);

    //sorting results by score
    BasicDBList res = sortByScoreAndLimit(results, this.limit);

    return BuildResponce(res);
  }

}
