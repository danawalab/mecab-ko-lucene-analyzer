package org.bitbucket.eunjeon.elasticsearch.util;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.search.highlight.Formatter;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.util.BytesRef;
import org.bitbucket.eunjeon.elasticsearch.highlight.TermHighlightingQuery;
import org.bitbucket.eunjeon.elasticsearch.product.analysis.ProductNameAnalyzerProvider;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortBuilder;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class SearchUtil {

	private static final TimeValue DEFAULT_SCROLL_KEEP_ALIVE = TimeValue.timeValueMinutes(1L);
	private static final TimeValue DEFAULT_SEARCH_TIME_OUT = new TimeValue(60, TimeUnit.SECONDS);
	private static final int DEFAULT_SCROLL_SIZE = 10000;

	private static Logger logger = Loggers.getLogger(SearchUtil.class, "");


	public static Map<String, Object> searchData(NodeClient client, String index){
		Map<String, Object> result = new HashMap<>();
		try {
			SearchRequest searchRequest = new SearchRequest();
			searchRequest.indices(index).source(SearchSourceBuilder.searchSource().query(QueryBuilders.matchAllQuery()).size(10000));
			SearchResponse response = client.search(searchRequest).actionGet();

			for(SearchHit hit : response.getHits().getHits()){
				String id = (String) hit.getSourceAsMap().get("id");
				String updatedTime = hit.getSourceAsMap().get("updatedTime") == null ? "" : (String) hit.getSourceAsMap().get("updatedTime");
				String appliedTime = hit.getSourceAsMap().get("appliedTime") == null ? "" : (String) hit.getSourceAsMap().get("appliedTime");
				int count = hit.getSourceAsMap().get("count") == null ? 0 : (Integer) hit.getSourceAsMap().get("count");
				result.put(id, count);
				result.put(id + "_appliedTime", appliedTime);
				result.put(id + "_updatedTime", updatedTime);
			}
		}catch (Exception e){
			logger.error("{}", e);
		}
		return result;
	}

	public static void upsertData(NodeClient client, String index, Map<String, Object> data){
		logger.info("{}", data);
		int cnt = (Integer) data.get("cnt");
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		try {
			Map<String, Object> record = new HashMap<String, Object>();
			record.put("id", data.get("id"));
			record.put("appliedTime", sdf.format(new Date()));
			record.put("count", cnt);
			UpdateRequest updateRequest = new UpdateRequest(index, (String) data.get("id"))
					.docAsUpsert(true).upsert(record, XContentType.JSON).doc(record, XContentType.JSON);
			UpdateResponse response = client.update(updateRequest).actionGet();
		}catch (Exception e){
			logger.error("{}", e);
		}
	}


	public static void deleteAllData(NodeClient client, String index) {
		BulkRequestBuilder builder = null;
		builder = client.prepareBulk();
		SearchHit[] hits = null;
		ClearScrollRequest clearScroll = null;
		Scroll scroll = null;
		String scrollId = null;

		try {
			QueryBuilder query = null;
			query = QueryBuilders.matchAllQuery();
			SearchSourceBuilder source = new SearchSourceBuilder();
			source.query(query);
			SearchRequest search = new SearchRequest(index.split("[,]"));
			clearScroll = new ClearScrollRequest();
			scroll = new Scroll(DEFAULT_SCROLL_KEEP_ALIVE);
			source.from(0);
			source.size(DEFAULT_SCROLL_SIZE);
			source.timeout(DEFAULT_SEARCH_TIME_OUT);
			search.source(source);
			search.scroll(scroll);
			SearchResponse response = client.search(search).get();
			hits = response.getHits().getHits();
			scrollId = response.getScrollId();
			clearScroll.addScrollId(scrollId);

			int totInx = 0;
			for (; hits != null && hits.length > 0;) {
				for (int inx = 0; inx < hits.length; inx++, totInx++) {
					DeleteRequest request = new DeleteRequest(index, hits[inx].getId());
					builder.add(request);
					if (totInx > 0 && totInx % 5000 == 0) {
						builder.execute().actionGet();
						builder = client.prepareBulk();
					}
				}
				SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
				scrollRequest.scroll(scroll);
				response = client.searchScroll(scrollRequest).get();
				hits = response.getHits().getHits();
				scrollId = response.getScrollId();
				clearScroll.addScrollId(scrollId);
			}
			if (totInx > 0) {
				builder.execute().actionGet();
			}
		} catch (Exception e) {
			logger.error("", e);
		}
	}

	public static long count(Client client, String index, QueryBuilder query) {
		long ret = 0;
		try {
			SearchRequest countRequest = new SearchRequest(index.split("[,]"));
			SearchSourceBuilder countSource = new SearchSourceBuilder().query(query).size(0).trackTotalHits(true);
			countRequest.source(countSource);
			SearchResponse countResponse = client.search(countRequest).get();
			ret = countResponse.getHits().getTotalHits().value;
		} catch (Exception e) {
			logger.error("", e);
		}
		return ret;
	}

	public static Iterator<Map<String, Object>> search(Client client, String index, QueryBuilder query, List<SortBuilder<?>> sortSet, HighlightBuilder highlight, int from, int size, boolean doScroll, DataModifier dataModifier) {
		Iterator<Map<String, Object>> ret = null;
		if (doScroll) {
			ret = new ScrollSearchResultIterator().doSearch(client, index, query, sortSet, highlight, dataModifier, from, size);
		} else {
			ret = new SearchResultIterator().doSearch(client, index, query, sortSet, highlight, dataModifier, from, size);
		}
		return ret;
	}

	public static String highlightString(String str, List<String> wordSet, List<String> tags) {
		String ret = null;
		if (str != null && (str = str.trim()).length() > 0) {
			TokenStream tstream = ProductNameAnalyzerProvider.getAnalyzer(str, false, false, true, false, true);
			List<BytesRef> terms = new ArrayList<>();
			for (String word : wordSet) {
				terms.add(new BytesRef(word.toUpperCase()));
			}
			String preTag = null;
			String postTag = null;
			if (tags.size() > 1) {
				preTag = tags.get(0);
				postTag = tags.get(1);
			}
			if (preTag != null && postTag != null) {
				TermHighlightingQuery query = new TermHighlightingQuery("", terms);
				Formatter formatter = null;
				if (preTag != null && !"".equals(preTag) && postTag != null && !"".equals(postTag)) {
					formatter = new SimpleHTMLFormatter(preTag, postTag);
				} else {
					formatter = new SimpleHTMLFormatter();
				}
				Encoder encoder = new SimpleHTMLEncoder();
				Scorer scorer = new QueryScorer(query);
				Highlighter highlighter = new Highlighter(formatter, encoder, scorer);
				try {
					ret = highlighter.getBestFragment(tstream, str);
				} catch (Exception e) {
					logger.debug("highlight error {} / {} / {}", str, wordSet, e.getMessage());
					logger.error("", e);
				}
				if (ret == null) {
					ret = str;
				}
			}
		}
		return ret;
	}

	public static abstract class DataModifier {
		public abstract void modify(Map<String, Object> map);
	}

	static abstract class AbstractSearchResultIterator implements Iterator<Map<String, Object>> {
		public static final String FIELD_ROWNUM = "_ROWNUM";
		public static final String FIELD_SORT = "_SORT";
		public static final String FIELD_HIGHLIGHT = "_HIGHLIGHT";
		abstract Iterator<Map<String, Object>> doSearch(Client client, String index, QueryBuilder query, List<SortBuilder<?>> sortSet, HighlightBuilder highlight, DataModifier dataModifier, int from, int size);

		static Map<String, Object> processHit(SearchHit hit, int rowNum, DataModifier dataModifier) {
			Map<String, Object> rowData;
			rowData = hit.getSourceAsMap();
			rowData.put(FIELD_ROWNUM, rowNum);
			rowData.put(FIELD_SORT, hit.getSortValues());
			if (dataModifier != null) {
				dataModifier.modify(rowData);
			}
			// NOTE: ES 하이라이터가 부실하여 커스텀 하이라이터를 사용
			// Map<String, String> highlight = new HashMap<>();
			// if (hit.getHighlightFields() != null) {
			// 	for (HighlightField field : hit.getHighlightFields().values()) {
			// 		StringBuilder value = new StringBuilder();
			// 		for (Text text : field.fragments()) {
			// 			if (text.hasString()) {
			// 				value.append(" ").append(text.string());
			// 			}
			// 		}
			// 		highlight.put(field.name(), String.valueOf(value).trim());
			// 	}
			// }
			// rowData.put(FIELD_HIGHLIGHT, highlight);
			return rowData;
		}
	}

	static class SearchResultIterator extends AbstractSearchResultIterator {
		private SearchHit[] hits;
		private int rowNum;
		private int hitsInx;
		private Map<String, Object> rowData;
		private TimeValue timeOut;
		private DataModifier dataModifier;



		@Override 
		public Iterator<Map<String, Object>> doSearch(Client client, String index, QueryBuilder query, List<SortBuilder<?>> sortSet, HighlightBuilder highlight, DataModifier dataModifier, int from, int size) {
			/**
			 * 단순 검색. 빠르지만 1만건 이상 검색결과 검색 불가능
			 **/
			try {
				SearchSourceBuilder source = new SearchSourceBuilder();
				if (sortSet != null) {
					for (SortBuilder<?> sort : sortSet) {
						source.sort(sort);
					}
				}
				// if (highlight != null) {
				// 	source.highlighter(highlight);
				// }
				this.dataModifier = dataModifier;
				source.query(query);
				SearchRequest search = new SearchRequest(index.split("[,]"));
				source.from(from);
				source.size(size);
				source.timeout(timeOut);
				search.source(source);
				SearchResponse response = client.search(search).get();
				hits = response.getHits().getHits();
				rowNum = from;
				rowData = null;
				return this;
			} catch (Exception e) {
				logger.debug("SEARCH ERROR : {} ( It may be over 10,000 records ) ", e.getMessage());
			}
			return null;
		}

		@Override public boolean hasNext() {
			boolean ret = false;
			if (rowData != null) { return true; }
			try {
				for (; hits != null && hits.length > 0;) {
					for (; hitsInx < hits.length;) {
						rowData = processHit(hits[hitsInx], rowNum, dataModifier);
						hitsInx++;
						rowNum++;
						break;
					}

					if (rowData != null) {
						ret = true;
						break;
					} else {
						ret = false;
						break;
					}
				}
			} catch (Exception e) {
				logger.error("", e);
			}
			return ret;
		}

		@Override public Map<String, Object> next() {
			Map<String, Object> ret = rowData;
			rowData = null;
			return ret;
		}

		public void close() { }
	}

	static class ScrollSearchResultIterator extends AbstractSearchResultIterator {

		private Client client;
		private SearchHit[] hits;
		private ClearScrollRequest clearScroll;
		private Scroll scroll;
		private String scrollId;
		private int rowNum;
		private int hitsInx;
		private Map<String, Object> rowData;
		private TimeValue scrollKeepAlive;
		private TimeValue timeOut;
		private DataModifier dataModifier;
		private int scrollSize;
		private int size;

		public ScrollSearchResultIterator() { 
			this(DEFAULT_SEARCH_TIME_OUT, DEFAULT_SCROLL_KEEP_ALIVE, DEFAULT_SCROLL_SIZE);
		}

		public ScrollSearchResultIterator(TimeValue timeOut, TimeValue scrollKeepAlive, int scrollSize) {
			this.scrollKeepAlive = scrollKeepAlive;
			this.timeOut = timeOut;
			this.scrollSize = scrollSize;
		}

		@Override
		public Iterator<Map<String, Object>> doSearch(Client client, String index, QueryBuilder query, List<SortBuilder<?>> sortSet, HighlightBuilder highlight, DataModifier dataModifier, int from, int size) {
			/**
			 * 스크롤 스트리밍 검색. 느리지만 1만건 이상 검색결과를 추출가능함.
			 **/
			try {
				this.client = client;
				SearchSourceBuilder source = new SearchSourceBuilder();
				if (sortSet != null) {
					for (SortBuilder<?> sort : sortSet) {
						source.sort(sort);
					}
				}
				// if (highlight != null) {
				// 	source.highlighter(highlight);
				// }
				this.dataModifier = dataModifier;
				source.query(query);
				SearchRequest search = new SearchRequest(index.split("[,]"));
				clearScroll = new ClearScrollRequest();
				scroll = new Scroll(scrollKeepAlive);
				source.from(0);
				source.size(scrollSize);
				source.timeout(timeOut);
				search.source(source);
				search.scroll(scroll);
				SearchResponse response = client.search(search).get();
				hits = response.getHits().getHits();
				scrollId = response.getScrollId();
				clearScroll.addScrollId(scrollId);
				for (rowNum = 0; hits != null && hits.length > 0 && rowNum < from;) {
					if (rowNum + hits.length <= from) { 
						rowNum += hits.length;
					} else {
						hitsInx = from - rowNum;
						rowNum = from;
						break;
					}
					SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
					scrollRequest.scroll(scroll);
					response = client.searchScroll(scrollRequest).get();
					hits = response.getHits().getHits();
					scrollId = response.getScrollId();
					clearScroll.addScrollId(scrollId);
				}
				this.size = size;
				rowData = null;
				return this;
			} catch (Exception e) {
				logger.error("", e);
			}
			return null;
		}

		@Override public boolean hasNext() {
			boolean ret = false;
			if (rowData != null) { return true; }
			try {
				for (; hits != null && hits.length > 0 && (size == -1 || size > 0);) {
					for (; hitsInx < hits.length && (size == -1 || size > 0);) {
						rowData = processHit(hits[hitsInx], rowNum, dataModifier);
						hitsInx++;
						rowNum++;
						if (size != -1) {
							size--;
						}
						break;
					}

					if (rowData != null) {
						ret = true;
						break;
					}

					SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
					scrollRequest.scroll(scroll);

					SearchResponse response = client.searchScroll(scrollRequest).get();
					hits = response.getHits().getHits();
					scrollId = response.getScrollId();
					clearScroll.addScrollId(scrollId);
					hitsInx = 0;
				}
				if (size == 0 || (hits == null && rowData == null)) {
					close(); 
					ret = false;
				}
			} catch (Exception e) {
				logger.error("", e);
			}
			return ret;
		}

		@Override public Map<String, Object> next() {
			Map<String, Object> ret = rowData;
			rowData = null;
			return ret;
		}

		public void close() {
			try {
				client.clearScroll(clearScroll).get();
			} catch (Exception ignore) { }
		}
	}
}