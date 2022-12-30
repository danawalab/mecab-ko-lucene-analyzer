package org.bitbucket.eunjeon.elasticsearch.product.analysis;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.bitbucket.eunjeon.elasticsearch.dict.analysis.ProductNameDictionary;
import org.bitbucket.eunjeon.elasticsearch.dict.analysis.ProductNameDictionary.DictionaryRepository;
import org.bitbucket.eunjeon.elasticsearch.dict.analysis.SourceDictionary;
import org.bitbucket.eunjeon.elasticsearch.dict.analysis.SynonymDictionary;
import org.bitbucket.eunjeon.elasticsearch.dict.analysis.TagProbDictionary;
import org.bitbucket.eunjeon.elasticsearch.dict.korean.PosTagProbEntry.TagProb;
import org.bitbucket.eunjeon.elasticsearch.index.DanawaBulkTextIndexer;
import org.bitbucket.eunjeon.elasticsearch.index.DanawaSearchQueryBuilder;
import org.bitbucket.eunjeon.elasticsearch.index.FastcatMigrateIndexer;
import org.bitbucket.eunjeon.elasticsearch.plugin.analysis.AnalysisMeCabKoStandardPlugin;
import org.bitbucket.eunjeon.elasticsearch.util.CharVector;
import org.bitbucket.eunjeon.elasticsearch.util.ContextStore;
import org.bitbucket.eunjeon.elasticsearch.util.RemoteNodeClient;
import org.bitbucket.eunjeon.elasticsearch.util.SearchUtil;
import org.bitbucket.eunjeon.elasticsearch.util.SearchUtil.DataModifier;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest.Metric;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.action.admin.cluster.node.info.PluginsAndModules;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.http.HttpInfo;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.plugins.PluginInfo;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder.Field;
import org.elasticsearch.search.sort.SortBuilder;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONWriter;
import tokenattributes.ExtraTermAttribute;
import tokenattributes.SynonymAttribute;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static java.util.regex.Matcher.quoteReplacement;
import static org.elasticsearch.rest.RestRequest.Method.GET;
import static org.elasticsearch.rest.RestRequest.Method.POST;

public class ProductNameAnalysisAction extends BaseRestHandler {

	private static Logger logger = Loggers.getLogger(ProductNameAnalysisAction.class, "");

	// 플러그인 내 전역저장소 설정
	private static final ContextStore contextStore = ContextStore.getStore(AnalysisMeCabKoStandardPlugin.class);

	private static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";
	private static final String BASE_URI = "/_analysis-product-name";
	private static final String ACTION_TEST = "test";
	private static final String ACTION_INFO_DICT = "info-dict";
	private static final String ACTION_FIND_DICT = "find-dict";
	private static final String ACTION_RELOAD_DICT = "reload-dict";
	private static final String ACTION_RESTORE_DICT = "restore-dict";
	private static final String ACTION_COMPILE_DICT = "compile-dict";
	private static final String ACTION_FULL_INDEX = "full-index";
	private static final String ACTION_FASTCAT_INDEX = "fastcat-index";
	private static final String ACTION_ANALYZE_TEXT = "analyze";
	private static final String ACTION_SEARCH = "search";
	private static final String ACTION_BUILD_QUERY = "build-query";
	private static final String ACTION_SYNONYM_LIST = "get-synonym-list";
	private static final String ACTION_ANALYZE_MULTI_PARAMS = "multi-analyze";
	private static final String ACTION_SEARCH_KEYWORD = "search-keyword";
	private static final String ACTION_CHECK_ENABLE = "check-dict";


	private static final String ES_DICTIONARY_INDEX = ".dsearch_dict";
	private static final String ES_DICT_FIELD_ID = "id";
	private static final String ES_DICT_FIELD_TYPE = "type";
	private static final String ES_DICT_FIELD_KEYWORD = "keyword";
	private static final String ES_DICT_FIELD_VALUE = "value";
	private static final String ES_INDEX_TOTALINDEX = "TOTALINDEX";

	private static final String TAG_BR = "<br/>";
	private static final String TAG_STRONG = "<strong>${TEXT}</strong>";
	private static final String REGEX_TAG_TEXT = "[$][{]TEXT[}]";
	private static final String COMMA = ",";
	private static final String TAB = "\t";

	private static final String ANALYZE_SET_FULL_STRING = "00_FULL_STRING_SET";
	private static final String ANALYZE_SET_STOPWORD = "01_RESTRICTED_SET";
	private static final String ANALYZE_SET_MODEL_NAME = "02_MODEL_NAME_SET";
	private static final String ANALYZE_SET_UNIT = "03_UNIT_SET";
	private static final String ANALYZE_SET_NORMAL = "04_NORMAL_SET";
	private static final String ANALYZE_SET_SYNONYM = "05_SYNONYM_SET";
	private static final String ANALYZE_SET_COMPOUND = "06_COMPOUND_SET";
	private static final String ANALYZE_SET_FINAL = "07_FINAL_SET";

	/**
	 * 텍스트 상세 분석 결과 분류
	 */
	private static final Map<String, String> ANALYSIS_RESULT_LABELS;
	static {
		Map<String, String> map = new HashMap<>();
		map.put(ANALYZE_SET_FULL_STRING, "전체질의어");
		map.put(ANALYZE_SET_STOPWORD, "불용어");
		map.put(ANALYZE_SET_MODEL_NAME, "모델명 규칙");
		map.put(ANALYZE_SET_UNIT, "단위명 규칙");
		map.put(ANALYZE_SET_NORMAL, "형태소 분리 결과");
		map.put(ANALYZE_SET_SYNONYM, "동의어 확장");
		map.put(ANALYZE_SET_COMPOUND, "복합명사");
		map.put(ANALYZE_SET_FINAL, "최종 결과");
		ANALYSIS_RESULT_LABELS = Collections.unmodifiableMap(map);
	}

	/**
	 * 전체색인 스레드
	 */
	private DanawaBulkTextIndexer indexingThread;

	/**
	 * FASTCAT 임포트 스레드
	 */
	private FastcatMigrateIndexer migratingThread;

	/**
	 * 액션등록, {action} 플레이스 홀더를 사용하여 변수로 받는다.
	 */
	@Override public List<Route> routes() {
		return unmodifiableList(asList(
			new Route(GET, BASE_URI + "/{action}"),
			new Route(POST, BASE_URI + "/{action}"),
			new Route(GET, BASE_URI + "/"),
			new Route(POST, BASE_URI + "/")));
	}

	@Override public String getName() {
		return "rest_handler_product_name_analysis";
	}

	/**
	 * 액션 핸들링 처리
	 */
	@Override protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
		final String action = request.param("action");
		final StringWriter buffer = new StringWriter();
		JSONWriter builder = new JSONWriter(buffer);

		if (ACTION_TEST.equals(action)) {
			testAction(request, client, builder);
			builder.object().key("action").value(action).endObject();
		} else if (ACTION_RELOAD_DICT.equals(action)) {
			reloadDictionary();
			builder.object().key("action").value(action).endObject();
		} else if (ACTION_INFO_DICT.equals(action)) {
			builder.object().key("action").value(action);
			infoDictionary(request, client, builder);
			builder.endObject();
		} else if (ACTION_FIND_DICT.equals(action)) {
			builder.object().key("action").value(action);
			findDictionary(request, client, builder);
			builder.endObject();
		} else if (ACTION_RESTORE_DICT.equals(action)) {
			restoreDictionary(request, client);
			builder.object().key("action").value(action).endObject();
		} else if (ACTION_COMPILE_DICT.equals(action)) {
			compileDictionary(request, client);
			builder.object().key("action").value(action).endObject();
		} else if (ACTION_FULL_INDEX.equals(action)) {
			int count = bulkIndex(request, client);
			builder.object().key("action").value(action);
			if (count > 0) {
				builder.key("working").value("true").key("count").value(count);
			} else {
				builder.key("working").value("false");
			}
			builder.endObject();
		} else if (ACTION_FASTCAT_INDEX.equals(action)) {
			int count = fastcatIndex(request, client);
			builder.object().key("action").value(action);
			if (count > 0) {
				builder.key("working").value("true").key("count").value(count);
			} else {
				builder.key("working").value("false");
			}
			builder.endObject();
		} else if (ACTION_ANALYZE_TEXT.equals(action)) {
			analyzeTextAction(request, client, builder);
		} else if (ACTION_SEARCH.equals(action)) {
			search(request, client, builder);
		} else if (ACTION_BUILD_QUERY.equals(action)) {
			buildQuery(request, client, builder);
		}else if(ACTION_ANALYZE_MULTI_PARAMS.equals(action)){
			builder.object();
			analyzeMultiParamsAction(request, client, builder);
			builder.endObject();
		} else if(ACTION_SYNONYM_LIST.equals(action)){
			builder.object();
			getSynonymListAction(request, client, builder);
			builder.endObject();
		} else if (ACTION_SEARCH_KEYWORD.equals(action)) {
			makeSearchKeyword(request, client, builder);
		} else if (ACTION_CHECK_ENABLE.equals(action)) {
			builder.object().key("loaded").value(getDictionary() != null);
			builder.endObject();
		}
		return channel -> {
			channel.sendResponse(new BytesRestResponse(RestStatus.OK, CONTENT_TYPE_JSON, buffer.toString()));
		};
	}

	/**
	 * REST 파라메터를 JSONObject 로 파싱한다.
	 */
	private JSONObject parseRequestBody(RestRequest request) {
		JSONObject ret = new JSONObject();
		try {
			ret = new JSONObject(new JSONTokener(request.content().utf8ToString()));
		} catch (Exception ignore) { }
		return ret;
	}

	/**
	 * 테스트용 액션
	 */
	public void testAction(RestRequest request, NodeClient client, JSONWriter writer) {
		logger.debug("TEST-ACTION");
	}

	/**
	 * 각 노드에 신호 전파
	 */
	private void distribute(NodeClient client, String action, JSONObject body, boolean selfDist) {
		String localNodeId = client.getLocalNodeId();
		NodesInfoRequest infoRequest = new NodesInfoRequest();
		infoRequest.clear()
			.addMetrics(Metric.JVM.metricName())
			.addMetrics(Metric.OS.metricName())
			.addMetrics(Metric.PROCESS.metricName())
			.addMetrics(Metric.HTTP.metricName())
			.addMetrics(Metric.PLUGINS.metricName())
			.addMetrics(Metric.INDICES.metricName());

		try {
			NodesInfoResponse response = client.admin().cluster().nodesInfo(infoRequest).get();
			List<NodeInfo> nodes = response.getNodes();
			for (NodeInfo node : nodes) {
				// 자기자신이 아닌경우에만 전파
				if (!selfDist && localNodeId.equals(node.getNode().getId())) {
					continue;
				}
				boolean hasPlugin = false;
				TransportAddress address = node.getInfo(HttpInfo.class).address().publishAddress();
				// 상품명분석기 플러그인을 가진 노드에만 전파
				List<PluginInfo> plugins = node.getInfo(PluginsAndModules.class).getPluginInfos();
				for (PluginInfo info : plugins) {
					if (hasPlugin = info.getClassname().equals(AnalysisMeCabKoStandardPlugin.class.getName())) {
						break;
					}
				}
				if (hasPlugin) {
					logger.debug("NODE: {}:{}", address.getAddress(), address.getPort());
					doRestRequest(address.getAddress(), address.getPort(), action, body);
				}
			}
		} catch (Exception e) {
			logger.error("", e);
		}
	}

	/**
	 * 특정 노드에 REST 신호를 보낸다
	 */
	private String doRestRequest(final String address, final int port, final String action, final JSONObject body) {
		SpecialPermission.check();
		return AccessController.doPrivileged((PrivilegedAction<String>) () -> {
			StringBuilder sb = new StringBuilder();
			HttpURLConnection con = null;
			OutputStream ostream = null;
			BufferedReader reader = null;
			try {
				String url = "http://" + address + ":" + port + BASE_URI + "/" + action;
				logger.debug("SEND REQUEST {}", url);
				con = (HttpURLConnection) new URL(url).openConnection();
				con.setRequestMethod("POST");
				con.setRequestProperty("Content-Type", "application/json");
				con.setDoOutput(true);
				ostream = con.getOutputStream();
				ostream.write(String.valueOf(body).getBytes());
				ostream.flush();
				int responseCode = con.getResponseCode();
				if (responseCode == HttpURLConnection.HTTP_CREATED) {
					logger.trace("RESPONSE:{}", responseCode);
					reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
					for (String rl; (rl = reader.readLine()) != null;) {
						sb.append(rl).append("\r\n");
					}
					logger.trace("RESPONSE:{}", sb);
				}
			} catch (Exception e) { 
				logger.error("", e);
			} finally {
				try { ostream.close(); } catch (Exception ignore) { }
				try { reader.close(); } catch (Exception ignore) { }
			}
			return String.valueOf(sb);
		});
	}

	/**
	 * 텍스트 상품명 상세분석
	 */
	private void analyzeTextAction(RestRequest request, NodeClient client, JSONWriter writer) {
		JSONObject jparam = new JSONObject();
		String index = request.param("index", ES_DICTIONARY_INDEX);
		String text = request.param("text", "");
		boolean detail = request.paramAsBoolean("detail", true);
		boolean useForQuery = request.paramAsBoolean("useForQuery", true);
		boolean useSynonym = request.paramAsBoolean("useSynonym", true);
		boolean useStopword = request.paramAsBoolean("useStopword", false);
		boolean useFullString = request.paramAsBoolean("useFullString", true);
		boolean test = false;
		if (!GET.equals(request.method())) {
			jparam = parseRequestBody(request);
			index = jparam.optString("index", ES_DICTIONARY_INDEX);
			text = jparam.optString("text", "");
			detail = jparam.optBoolean("detail", true);
			useForQuery = jparam.optBoolean("useForQuery", true);
			useSynonym = jparam.optBoolean("useSynonym", true);
			useStopword = jparam.optBoolean("useStopword", false);
			useFullString = jparam.optBoolean("useFullString", true);
			test = jparam.optBoolean("test", false);
		}

		TokenStream stream = null;
		try {
			stream = ProductNameAnalyzerProvider.getAnalyzer(text, useForQuery, useSynonym, useStopword, useFullString, false);
			analyzeTextDetailWriteJSON(client, text, stream, index, detail, writer);
		} finally {
			try { stream.close(); } catch (Exception ignore) { }
		}

		if (test) {
			distribute(client, ACTION_TEST, jparam, true);
		}
	}

	/**
	 * 텍스트 상품명 분석
	 * 다중 파라미터 처리
	 */
	private void analyzeMultiParamsAction(RestRequest request, NodeClient client, JSONWriter writer) {
		JSONObject jparam = new JSONObject();

		logger.debug("multi-params Action");
		// 인덱스는 고정
		String index = ES_DICTIONARY_INDEX;

		// 검색어 확장
		boolean useForQuery = false;
		boolean useSynonym = true;
		boolean useStopword = false;
		boolean useFullString = true;

		Map<String, String> analyzeMap = new HashMap<>();
		if (!GET.equals(request.method())) {
			// POST 처리
			jparam = parseRequestBody(request);
			for (String key : jparam.keySet()) {
				analyzeMap.put(key, jparam.optString(key, ""));
			}
		} else {
			// GET 처리
			try {
				for (String key : request.params().keySet()) {
					if (key.equals("action") || key.equals("pretty")) continue;
					analyzeMap.put(key, URLDecoder.decode(request.param(key, ""), "UTF-8"));
				}
			} catch (UnsupportedEncodingException e) {
				logger.error("UnsupportedEncodingException Catched !!! >>>> \n" + e.getMessage());
				writer.key("success").value(false);
				logger.debug("error >>> {}", e);
			}
		}

		TokenStream stream = null;
		writer.key("result").object();
		try {
			for(String key : analyzeMap.keySet()){
				String value = analyzeMap.get(key);
				logger.debug("key : {} :::::: value : {}", key, value);
				stream = ProductNameAnalyzerProvider.getAnalyzer(value, useForQuery, useSynonym, useStopword, useFullString, false);
				List<String> list = analyzeMultiParamsText(client, value, stream, index);
				if(list.size() != 0){
					writer.key(key).array();
					for(String item : list){
						writer.value(item);
					}
					writer.endArray();
				}
				stream.close();
			}
		} catch (Exception ignore){
			writer.endObject();
			writer.key("success").value(false);
			logger.debug("exception >>> {}", ignore);
			logger.error("Exception Catched !!! >>>> \n" + ignore.getMessage());
		} finally {
			writer.endObject();
			writer.key("success").value(true);
		}
	}

	/**
	 * 단방향 동의어 인지 확인
	 */
	public static boolean isOneWaySynonym(NodeClient client, String index, String word) {
		boolean ret = false;
		// 단방향 판단
		if (client != null && index != null && !"".equals(index)) {
			// 색인을 사용하는 경우
			BoolQueryBuilder query = QueryBuilders.boolQuery()
				.must(QueryBuilders.matchQuery(ES_DICT_FIELD_TYPE, ProductNameDictionary.DICT_SYNONYM.toUpperCase()))
				.must(QueryBuilders.matchQuery(ES_DICT_FIELD_KEYWORD, word)
				);
			logger.trace("Q:{}", query);
			long count = 0;
			if ((count = SearchUtil.count(client, index, query)) > 0) {
				ret = true;
			}
			logger.trace("SYNONYM:{}-{} = {}", index, word, count);
		} else {
			// 메모리 사전을 이용하는 경우
			ProductNameDictionary dictionary = getDictionary();
			SynonymDictionary synonyms = dictionary.getDictionary(ProductNameDictionary.DICT_SYNONYM, SynonymDictionary.class);
			ret = ProductNameDictionary.isOneWaySynonym(CharVector.valueOf(word), synonyms.map());
		}
		return ret;
	}

	/**
	 * 상품명 상세분석.
	 * analyzer 를 통해 텍스트를 분석해 출력한다.
	 * 다나와 관리모듈에 맞는 형식으로 제작
	 */
	public static void analyzeTextDetail(NodeClient client, String text, TokenStream stream, String index,
		Map<String, List<List<String>>> result, Map<String, Boolean> synonymWayMap, Map<String, List<String>> synonymMap) {
		CharTermAttribute termAttr = stream.addAttribute(CharTermAttribute.class);
		TypeAttribute typeAttr = stream.addAttribute(TypeAttribute.class);
		ExtraTermAttribute extAttr = stream.addAttribute(ExtraTermAttribute.class);
		SynonymAttribute synAttr = stream.addAttribute(SynonymAttribute.class);
		OffsetAttribute offAttr = stream.addAttribute(OffsetAttribute.class);
		List<List<String>> wordList = null;
		List<String> words = null;
		List<String> words2 = null;
		List<CharSequence> synonyms = synAttr.getSynonyms();
		for (String key : ANALYSIS_RESULT_LABELS.keySet()) {
			result.put(key, new ArrayList<>());
		}

		String term = null;
		String trimFullTerm = text.replaceAll(" ", "");
		String type = null;
		String setName = null;
		String setNamePrev = null;
		int[] offset = { 0, 0 };
		int[] offsetPrev = { 0, 0 };
		boolean oneWaySynonym = false;

		// 2개 초과의 텀에 대해 유사어 확장
		// 이전 텀, 이전 이전텀
		String prevTerm = null;
		String prePrevTerm = null;
		ProductNameDictionary dictionary = getDictionary();
		SynonymDictionary synonymDict = dictionary.getDictionary(ProductNameDictionary.DICT_SYNONYM, SynonymDictionary.class);

		try {
			stream.reset();
			while (stream.incrementToken()) {
				setNamePrev = setName;
				offsetPrev = new int[] { offset[0], offset[1] };
				term = String.valueOf(termAttr);
				type = typeAttr.type();
				offset = new int[] { offAttr.startOffset(), offAttr.endOffset() };
				logger.trace("TERM:{} / {}", term, type);
				if (!ANALYZE_SET_FULL_STRING.equals(setNamePrev) && offset[0] < offsetPrev[1]) {
					// 모델명 / 단위명 등 뒤에 나온 부속단어 (색인시)
					wordList = result.get(setNamePrev);
					if (wordList != null) {
						words = wordList.get(wordList.size() - 1);
						words.add(term);
						setName = setNamePrev;
						offset = new int[] { offsetPrev[0], offsetPrev[1] };
						setAnalyzedResult(result, term, ANALYZE_SET_NORMAL, ANALYZE_SET_FINAL);
					}
				} else if (ProductNameTokenizer.FULL_STRING.equals(type)) {
					// 전체 단어
					setName = ANALYZE_SET_FULL_STRING;
					setAnalyzedResult(result, term, ANALYZE_SET_FULL_STRING, ANALYZE_SET_FINAL);
				} else if (ProductNameTokenizer.MODEL_NAME.equals(type)) {
					// 모델명의 경우 색인시와 질의시 추출방법이 서로 다르다.
					setName = ANALYZE_SET_MODEL_NAME;
					setAnalyzedResult(result, term, ANALYZE_SET_MODEL_NAME, ANALYZE_SET_NORMAL, ANALYZE_SET_FINAL);
				} else if (ProductNameTokenizer.UNIT.equals(type)) {
					// 단위명
					setName = ANALYZE_SET_UNIT;
					setAnalyzedResult(result, term, ANALYZE_SET_UNIT, ANALYZE_SET_NORMAL, ANALYZE_SET_FINAL);
				} else if (ProductNameTokenizer.COMPOUND.equals(type)) {
					setName = ANALYZE_SET_COMPOUND;
					setAnalyzedResult(result, term, ANALYZE_SET_COMPOUND, ANALYZE_SET_NORMAL, ANALYZE_SET_FINAL);
				} else if (ProductNameTokenizer.STOPWORD.equals(type)) {
					setName = ANALYZE_SET_STOPWORD;
					setAnalyzedResult(result, term, ANALYZE_SET_STOPWORD);
				} else {
					// 일반단어
					setName = ANALYZE_SET_NORMAL;
					setAnalyzedResult(result, term, ANALYZE_SET_NORMAL, ANALYZE_SET_FINAL);
				}

				if ((synonyms = synAttr.getSynonyms()) != null && synonyms.size() > 0) {
					wordList = result.get(ANALYZE_SET_SYNONYM);
					wordList.add(words = new ArrayList<>());
					words.add(term);
					wordList = result.get(setName);
					words2 = wordList.get(wordList.size() - 1);
					oneWaySynonym = isOneWaySynonym(client, index, term);
					synonymWayMap.put(term, oneWaySynonym);
					synonymMap.put(term, words);
					logger.trace("SYNONYM [{}] {} / {} / {}", setName, term, oneWaySynonym, synonyms);
					for (CharSequence synonym : synonyms) {
						String s = String.valueOf(synonym);
						words.add(s);
						if (!(ANALYZE_SET_NORMAL.equals(setName)
							|| ANALYZE_SET_MODEL_NAME.equals(setName))) {
							words2.add(s);
						}
						setAnalyzedResult(result, s, ANALYZE_SET_FINAL);
					}
				}

				if(!"".equals(term) && !ProductNameTokenizer.FULL_STRING.equals(type)){
					//전체 단어
					//이전 텀 처리
					if(prevTerm != null){
						String combineTermTwo = prevTerm.concat(term);
						if(!combineTermTwo.equals(trimFullTerm)){
							setAnalyzeSynonym(synonymDict, combineTermTwo, client, index, result, synonymWayMap, synonymMap, words, words2, wordList, setName);
						}

						//이전 + 이전텀 처리
						if(prePrevTerm != null){
							String combineTermThree = prePrevTerm.concat(combineTermTwo);
							setAnalyzeSynonym(synonymDict, combineTermThree, client, index, result, synonymWayMap, synonymMap, words, words2, wordList, setName);
						}
					}
					prePrevTerm = prevTerm;
					prevTerm = term;
				}

				if (!ANALYZE_SET_FULL_STRING.equals(setName)) {
					Iterator<String> iter = extAttr.iterator();
					if (iter != null && iter.hasNext()) {
						wordList = result.get(setName);
						words = wordList.get(wordList.size() - 1);
						while (iter.hasNext()) {
							String s = iter.next();
							logger.trace("EXT [{}] {} / {} / {} / {}", setName, term, s, words, wordList);
							words.add(s);
							setAnalyzedResult(result, s, ANALYZE_SET_NORMAL, ANALYZE_SET_FINAL);
							synonyms = synAttr.getSynonyms();
							if (synonyms != null && synonyms.size() > 0) {
								oneWaySynonym = isOneWaySynonym(client, index, s);
								synonymWayMap.put(s, oneWaySynonym);
								synonymMap.put(term, words);
								logger.trace("EXT-SYN [{}] {} / {} / {} / {} / {}", setName, term, s, oneWaySynonym, synonyms, wordList);
								setAnalyzedResult(result, s, ANALYZE_SET_SYNONYM);
								wordList = result.get(ANALYZE_SET_SYNONYM);
								List<String> list = wordList.get(wordList.size() - 1);
								for (CharSequence synonym : synonyms) {
									s = String.valueOf(synonym);
									list.add(s);
									setAnalyzedResult(result, s, ANALYZE_SET_FINAL);
								}
							}
						}
					}
				}
			}
			logger.trace("SYNONYM-WAY : {}", synonymWayMap);
		} catch (Exception e) {
			logger.error("", e);
		}
	}
	// 랭킹뷰어, 관리도구에서 사용되는 동의어 확장용 메소드
	private static void setAnalyzeSynonym(SynonymDictionary dictionary, String combineTerm, NodeClient client, String index, Map<String, List<List<String>>> result, Map<String, Boolean> synonymWayMap,
										  Map<String, List<String>> synonymMap, List<String> words, List<String> words2, List<List<String>> wordList, String setName) {
		CharSequence[] dictData = dictionary.get(new CharVector(combineTerm));
		if(dictData != null){
			List<CharSequence> syns = getAnalyzeSynonym(combineTerm);
			wordList = result.get(ANALYZE_SET_SYNONYM);
			wordList.add(words = new ArrayList<>());
			words.add(combineTerm);
			wordList = result.get(setName);
			words2 = wordList.get(wordList.size() - 1);
			boolean oneWaySynonym = isOneWaySynonym(client, index, combineTerm);
			synonymWayMap.put(combineTerm, oneWaySynonym);
			synonymMap.put(combineTerm, words);

			for (CharSequence syn : syns) {
				String s = String.valueOf(syn);
				words.add(s);
				if (!(ANALYZE_SET_NORMAL.equals(setName)
						|| ANALYZE_SET_MODEL_NAME.equals(setName))) {
					words2.add(s);
				}
			}
		}
	}

	// 랭킹뷰어, 관리도구에서 사용되는 동의어 확장용 메소드
	private static List<CharSequence> getAnalyzeSynonym(String combineTerm) {
		TokenStream analyzerStream;
		List<CharSequence> synonyms;
		List<CharSequence> result = new ArrayList<>();

		// 동의어 확장
		try {
			analyzerStream = ProductNameAnalyzerProvider.getAnalyzer(combineTerm, true, true, true, true, false);
			SynonymAttribute synAtr = analyzerStream.addAttribute(SynonymAttribute.class);
			TypeAttribute typeAttr = analyzerStream.addAttribute(TypeAttribute.class);

			analyzerStream.reset();
			while (analyzerStream.incrementToken()){
				if ((synonyms = synAtr.getSynonyms()) != null && synonyms.size() > 0) {
					for (CharSequence synonym : synonyms) {
						String type = typeAttr.type();

						if (type.equals(ProductNameTokenizer.FULL_STRING)) {
							result.add(synonym);
						}
					}
				}
			}
		} catch (Exception e){
			logger.error("", e);
		}

		return result;
	}

	public static void analyzeTextDetailWriteJSON(NodeClient client, String text, TokenStream stream, String index, boolean detail, JSONWriter writer) {
		Map<String, List<List<String>>> result = new HashMap<>();
		Map<String, Boolean> synonymWayMap = new HashMap<>();
		Map<String, List<String>> synonymMap = new HashMap<>();
		List<List<String>> wordList = null;
		List<String> words = null;
		analyzeTextDetail(client, text, stream, index, result, synonymWayMap, synonymMap);

		try {
			writer.object()
				.key("query").value(text)
				.key("result").array();
			if (detail) {
				for (String key : new TreeSet<String>(result.keySet())) {
					String label = ANALYSIS_RESULT_LABELS.get(key);
					wordList = result.get(key);
					logger.trace("TYPE:{} / {}", label, wordList);
					StringBuilder analyzed = new StringBuilder();

					if (ANALYZE_SET_FULL_STRING.equals(key)) {
						if (wordList != null && wordList.size() > 0) {
							words = wordList.get(0);
							if (words != null && words.size() > 0) {
								analyzed.append(words.get(0));
							}
						}
					} else if (ANALYZE_SET_STOPWORD.equals(key)) {
						for (List<String> item : wordList) {
							if (analyzed.length() > 0) { analyzed.append(COMMA).append(" "); }
							if (item.size() > 0) {
								analyzed.append(item.get(0));
							}
						}
					} else if (ANALYZE_SET_MODEL_NAME.equals(key)) {
						for (List<String> item : wordList) {
							if (analyzed.length() > 0) { analyzed.append(TAG_BR); }

							// 모델명규칙 풀텀 가장 뒤에 나오도록 변경
							// 2021-03-17 선지호
							String fullTermKeyword = item.get(0);
							item.remove(0);
							item.add(fullTermKeyword);

							for (int inx = 0; inx < item.size(); inx++) {
								String w = item.get(inx);
								if (inx == 0) {
									analyzed.append(TAG_STRONG.replaceAll(REGEX_TAG_TEXT, quoteReplacement(fullTermKeyword)))
											.append(" ( ");
									analyzed.append(w);
								} else {
									if (inx > 0) { analyzed.append(", "); }
									analyzed.append(w);
								}
							}
							analyzed.append(" ) ");
						}
					} else if (ANALYZE_SET_UNIT.equals(key)) {
						for (List<String> item : wordList) {
							if (analyzed.length() > 0) { analyzed.append(TAG_BR); }
							String word = null;
							StringBuilder data = new StringBuilder();
							for (int inx = 0; inx < item.size(); inx++) {
								String w = item.get(inx);
								if (inx == 0) {
									word = w;
									analyzed.append(TAG_STRONG.replaceAll(REGEX_TAG_TEXT, quoteReplacement(w))).append(" : ");
								} else {
									if (inx > 1) { data.append(", "); }
									data.append(w);
								}
							}
							if (synonymWayMap.containsKey(word)) {
								analyzed.append(data).append(TAG_BR).append(" >>> 동의어 : ");
								data = new StringBuilder();
								words = synonymMap.get(word);
								for (int inx = 1; inx < words.size(); inx++) {
									String w = words.get(inx);
									if (inx > 1) { data.append(", "); }
									data.append(w);
								}
								analyzed.append(data);
							} else {
								analyzed.append(data);
							}
						}
					} else if (ANALYZE_SET_NORMAL.equals(key)) {
						for (List<String> item : wordList) {
							if (analyzed.length() > 0) {
								analyzed.append(COMMA).append(" ");
							}
							if (item.size() > 0) {
								analyzed.append(item.get(0));
							}
						}
					} else if (ANALYZE_SET_SYNONYM.equals(key)) {
						for (List<String> item : wordList) {
							if (analyzed.length() > 0) { analyzed.append(TAG_BR); }
							String word = null;
							StringBuilder data = new StringBuilder();
							for (int inx = 0; inx < item.size(); inx++) {
								String w = item.get(inx);
								if (inx == 0) {
									word = w;
									analyzed.append(TAG_STRONG.replaceAll(REGEX_TAG_TEXT, quoteReplacement(w))).append(" : ");
								} else {
									if (inx > 1) { data.append(", "); }
									data.append(w);
								}
							}
							if (synonymWayMap.containsKey(word) && synonymWayMap.get(word)) {
								analyzed.append(data).append(TAG_BR).append(" >>> 단방향 : ").append(data);
							} else {
								analyzed.append(data);
							}
						}
					} else if (ANALYZE_SET_COMPOUND.equals(key)) {
						for (List<String> item : wordList) {
							if (analyzed.length() > 0) { analyzed.append(TAG_BR); }
							for (int inx = 0; inx < item.size(); inx++) {
								String w = item.get(inx);
								if (inx == 0) {
									analyzed.append(TAG_STRONG.replaceAll(REGEX_TAG_TEXT, quoteReplacement(w))).append(" : ");
								} else {
									if (inx > 1) { analyzed.append(", "); }
									analyzed.append(w);
								}
							}
						}
					} else if (ANALYZE_SET_FINAL.equals(key)) {
						for (List<String> item : wordList) {
							if (analyzed.length() > 0) { analyzed.append(COMMA).append(" "); }
							if (item.size() > 0) {
								analyzed.append(item.get(0));
							}
						}
					}
					writer.object()
						.key("key").value(label)
						.key("value").value(analyzed);
					writer.endObject();
				}
			} else {
				wordList = result.get(ANALYZE_SET_FINAL);
				logger.trace("RESULT:{}", wordList);
				for (List<String> item : wordList) {
					if (item.size() > 0) {
						writer.value(item.get(0));
					}
				}
			}
			writer
				.endArray()
				.key("success").value(true)
				.endObject();
		} catch (Exception e) {
			logger.error("", e);
		}
	}

	/**
	 * 검색어 확장 각텀의 확장된 검색어들을 만든다
	 */
	public static void expansionKeyword(String text, TokenStream stream, JSONWriter writer) {
		CharTermAttribute termAttr = stream.addAttribute(CharTermAttribute.class);
		TypeAttribute typeAttr = stream.addAttribute(TypeAttribute.class);
		ExtraTermAttribute extAttr = stream.addAttribute(ExtraTermAttribute.class);
		SynonymAttribute synAttr = stream.addAttribute(SynonymAttribute.class);
		OffsetAttribute offAttr = stream.addAttribute(OffsetAttribute.class);

		List<Object> termWords;
		// 검색어 확장을 위한 변수
		List<Object> extraTermWords;
		ProductNameDictionary dictionary = getDictionary();
		SynonymDictionary synonymDict = dictionary.getDictionary(ProductNameDictionary.DICT_SYNONYM, SynonymDictionary.class);

		List<CharSequence> synonyms;

		List<String> tempTermList = new ArrayList<>();
		Map<String, Object> hash = new LinkedHashMap<>();
		// 풀텀, 합성텀에 대해 결과를 저장하는 변수
		Map<String, Object> combineTermSynonym = new HashMap<>();
		// 확장어에 대해 결과를 저장하는 변수
		Map<String, Object> extensionSynonym = null;

		int[] offset = {0, 0};
		int[] offsetPrev = {0, 0};

		try {
			stream.reset();
			while (stream.incrementToken()) {
				offsetPrev = new int[]{offset[0], offset[1]};
				String term = String.valueOf(termAttr);
				termWords = new ArrayList<>();
				extraTermWords = new ArrayList<>();
				String type = typeAttr.type();
				offset = new int[]{offAttr.startOffset(), offAttr.endOffset()};
				logger.trace("TERM:{} / {}", term, type);
				//풀텀일 경우는 펄텀유사어 따로 처리
				if (type.equals(ProductNameTokenizer.FULL_STRING)) {

					if ((synonyms = synAttr.getSynonyms()) != null && synonyms.size() > 0) {

						termWords = new ArrayList<>();
						ArrayList<Object> termList = divideTerm(text);
						termWords.add(termList);

						logger.trace("FULLTERM SYNONYM {} / {} ", term, synonyms);

						for (CharSequence synonym : synonyms) {
							String s = String.valueOf(synonym);

							String[] synonymList = s.split(" ");

							if(synonymList.length > 1){
								termWords.add(synonymList);
							}else{
								termWords.add(s);
							}
						}
						combineTermSynonym.put(term, termWords);

					}
				}else{
					// 유사어 확장을 위해 텀 적재
					tempTermList.add(term);
					//logger.info("{} - {}", offset, offsetPrev);
					if (offset[0] < offsetPrev[1]) {
						termWords.add(term);
						offset = new int[]{offsetPrev[0], offsetPrev[1]};
					}else{
						termWords.add(term);
					}

					if ((synonyms = synAttr.getSynonyms()) != null && synonyms.size() > 0) {

						logger.trace("SYNONYM {} / {} ", term, synonyms);
						for (CharSequence synonym : synonyms) {
							String s = String.valueOf(synonym);

							String[] synonymList = s.split(" ");

							if(synonymList.length > 1){
								termWords.add(synonymList);
							}else{
								termWords.add(s);
							}
						}
					}

					Iterator<String> iter = extAttr.iterator();
					if (iter != null && iter.hasNext()) {

						//termWords = new ArrayList<>();
						//확장어 원본 입력
						//extraTermWords.add(term);
						while (iter.hasNext()) {
							String s = iter.next();
							logger.trace("EXT {} / {} / {}", term, s, termWords);
							extraTermWords.add(s);
							synonyms = synAttr.getSynonyms();
							if (synonyms != null && synonyms.size() > 0) {
								extensionSynonym = new HashMap<>();
								List<String> extSynonymList = new ArrayList<>();
								//확장어의 동의어가 있을 경우 원본 확장어 리스트에서 삭제 (이후 확장어 동의어 리스트 적재)
								extraTermWords.remove(s);

								logger.trace("EXT-SYN {} / {} / {}", term, s, synonyms);

								//확장어 동의어가 하나 혹은 여러개일 떄 별도?
								for (CharSequence synonym : synonyms) {
									logger.trace("synonym : {}" , synonym);
									extSynonymList.add(String.valueOf(synonym));
								}
								logger.trace("synonymList : {} - {}", s, extSynonymList);
								extensionSynonym.put(s,extSynonymList);
								if(extensionSynonym.size() > 0) {
									extraTermWords.add(extensionSynonym);
								}
							}
						}
						//extraTermWords.add(extAnalyeTerm);
					}
					if (extraTermWords.size() > 0) {
						termWords.add(extraTermWords);
					}
				}
				logger.trace("term : {} - {}", term, termWords);
				
				//풀텀이 아닐 경우만. 풀텀(유사어)는 이미 fullTermHash에 적재됨
				if (!type.equals(ProductNameTokenizer.FULL_STRING)) {
					if (termWords.size() == 0) {
						hash.put(term, new Object[]{term});
					}else{
						hash.put(term, termWords.toArray());
					}
				}
			}
			// 2개 초과의 텀에 대해 유사어 확장
			// combineTermSynonym
			if(tempTermList.size() > 2){
				// 이전 텀, 이전 이전텀
				String prevTerm = null;
				String prePrevTerm = null;
				String trimFullTerm = text.replaceAll(" ", "");

				for(int i = 0; i<tempTermList.size(); i++){
					String term = tempTermList.get(i);

					// 이전 텀 처리
					if(prevTerm != null){
						String combineTermTwo = prevTerm.concat(term);
						putSynonymData(combineTermSynonym, synonymDict, combineTermTwo);

						// 이전 이전텀 처리
						if(prePrevTerm != null){
						    String combineTermThree = prePrevTerm.concat(combineTermTwo);

							// 조합형태소 3개이며 풀텀아니면
							if(i == 2){
								// 풀텀은 처리 제외
								if(!combineTermThree.equals(trimFullTerm)){
									putSynonymData(combineTermSynonym, synonymDict, combineTermThree);
								}
							} else {
								putSynonymData(combineTermSynonym, synonymDict, combineTermThree);
							}
						}
					}
					prePrevTerm = prevTerm;
					prevTerm = term;
				}
			}

			writer.object()
					.key("query").value(text)
					.key("result").value(hash)
					.key("synonym").value(combineTermSynonym)
					.endObject();
		} catch (Exception e) {
			logger.error("", e);
		}
	}

	// combineTermSynonym 최종적으로 리턴할 유사어 맵, synonymDict 유사어 사전, combineTermTwo 찾을 단어
	private static void putSynonymData(Map<String, Object> combineTermSynonym, SynonymDictionary synonymDict, String combineTerm) {
		CharSequence[] dictData = synonymDict.get(new CharVector(combineTerm));

		// 동의어가 있을 경우
		if(dictData != null){
			combineTermSynonym.put(combineTerm, removeDuplicates(findSynonymTerm(combineTerm, dictData)));
		}
	}


	private static ArrayList<Object> findSynonymTerm(String combineTerm, CharSequence[] dictData) {
		ArrayList<Object> result = new ArrayList<>();

		result.add(divideTerm(combineTerm));
		for (Object v : dictData) {
			String value = String.valueOf(v);
			TokenStream analyzerStream = null;

			// 동의어 확장
			try {
				analyzerStream = ProductNameAnalyzerProvider.getAnalyzer(value, true, false, false, false, false);
				CharTermAttribute termAtr = analyzerStream.addAttribute(CharTermAttribute.class);
				analyzerStream.reset();
				ArrayList<Object> subResult = new ArrayList<>();
				while (analyzerStream.incrementToken()){
					String temp = String.valueOf(termAtr);
					subResult.add(temp);
				}
				result.add(subResult);
			} catch (Exception e){
				logger.error("", e);
			}
		}

		return result;
	}

	// 키워드를 형태소 단위로 분리하는 메소드
	private static ArrayList<Object> divideTerm(String keyword) {
		ArrayList<Object> result = new ArrayList<>();

		String value = String.valueOf(keyword);
		TokenStream analyzerStream = null;

		// 동의어 확장
		try {
			analyzerStream = ProductNameAnalyzerProvider.getAnalyzer(value, true, false, false, false, false);
			CharTermAttribute termAtr = analyzerStream.addAttribute(CharTermAttribute.class);
			analyzerStream.reset();
			while (analyzerStream.incrementToken()){
				String temp = String.valueOf(termAtr);
				result.add(temp);
			}
		} catch (Exception e){
			logger.error("", e);
		}

		return result;
	}

	// ArrayList 중복제거
	private static <T> ArrayList<T> removeDuplicates(ArrayList<T> list)
	{
		ArrayList<T> newList = new ArrayList<T>();

		for (T element : list) {
			if (!newList.contains(element)) {
				newList.add(element);
			}
		}

		return newList;
	}

	/**
	 * 다중 파라미터 분석.
	 * analyzer 를 통해 텍스트를 분석해 출력한다.
	 * 다나와 관리모듈에 맞는 형식으로 제작
	 */
	public static List<String> analyzeMultiParamsText(NodeClient client, String text, TokenStream stream,  String index) {
		List<String> resultList = new ArrayList<>();
		Map<String, List<List<String>>> result = new HashMap<>();
		Map<String, Boolean> synonymWayMap = new HashMap<>();
		Map<String, List<String>> synonymMap = new HashMap<>();
		List<List<String>> wordList = null;
		analyzeTextDetail(client, text, stream, index, result, synonymWayMap, synonymMap);
		try {
			wordList = result.get(ANALYZE_SET_FINAL);
			logger.trace("RESULT:{}", wordList);
			for (List<String> item : wordList) {
				if (item.size() > 0) {
					resultList.add(item.get(0));
				}
			}
		} catch (Exception e) {
			logger.error("", e);
		}
		return resultList;
	}

	/**
	 * 상세분석시 각 분류별 결과를 분류 맵핑에 입력해 준다.
	 */
	private static void setAnalyzedResult(Map<String, List<List<String>>> result, String term, String... types) {
		List<List<String>> wordList = null;
		List<String> words = null;
		for (String type : types) {
			wordList = result.get(type);
			wordList.add(words = new ArrayList<>());
			words.add(term);
		}
	}

	/**
	 * 상품명사전을 메모리에 재적제 시킨다
	 */
	private void reloadDictionary() {
		if (contextStore.containsKey(ProductNameDictionary.PRODUCT_NAME_DICTIONARY)) {
			ProductNameDictionary.reloadDictionary();
		}
	}

	/**
	 * 요청받은 상품명사전을 엘라스틱서치에서 읽어서 메모리로 로딩한뒤 파일로 저장한다.
	 * (ES 색인 사용) - 변경된 부분
	 * 그후 전체 사전을 재로딩한다.
	 */
	private void compileDictionary(RestRequest request, NodeClient client) {
		JSONObject jparam = new JSONObject();
		String index = request.param("index", ES_DICTIONARY_INDEX);
		String type = request.param("type", null);
		boolean exportFile = request.paramAsBoolean("exportFile", false);
		boolean distribute = request.paramAsBoolean("distribute", false);
		String host = request.param("host", null);
		int port = request.paramAsInt("port", 9200);
		String username = request.param("username", null);
		String password = request.param("password", null);

		if (!GET.equals(request.method())) {
			/* POST */
			jparam = parseRequestBody(request);
			index = jparam.optString("index", ES_DICTIONARY_INDEX);
			type = jparam.optString("type", null);
			exportFile = jparam.optBoolean("exportFile", false);
			distribute = jparam.optBoolean("distribute", false);
			host = jparam.optString("host", null);
			port = jparam.optInt("port", 9200);
			username = jparam.optString("username", null);
			password = jparam.optString("password", null);
		} else {
			/* GET */
			jparam.put("index", index);
			jparam.put("type", type);
			jparam.put("exportFile", exportFile);
			jparam.put("distribute", distribute);
			jparam.put("distribute", distribute);
			jparam.put("host", host);
			jparam.put("port", port);
			jparam.put("username", username);
			jparam.put("password", password);
		}

		// 사전소스가 다른 클러스터의 것을 참조하고 있다면..
		DictionarySource repo = null;
		if (host != null && !"".equals(host)) {
			RemoteNodeClient remoteNodeClient = new RemoteNodeClient(client.settings(), client.threadPool(), ES_DICTIONARY_INDEX, host, port, username, password);
			repo = new DictionarySource(remoteNodeClient, index);
		} else {
			repo = new DictionarySource(client, index);
		}

		//type으로 요청이 들어온 사전만 컴파일 한다.
//		ProductNameDictionary.compileDictionary(repo, type, exportFile);
		ProductNameDictionary.compileDictionary(client, repo, type, exportFile);

		//모든 사전을 다시 로딩한다.
		ProductNameDictionary.reloadDictionary();

		if (distribute) {
			//받는 노드에서 재전파 요청을 하면 안되므로, distribute 를 false 로 보낸다.
			jparam.put("distribute", false);
			distribute(client, ACTION_COMPILE_DICT, jparam, false);
		}

	}

	/**
	 * 사전정보 추출
	 */
	private void infoDictionary(RestRequest request, NodeClient client, JSONWriter builder) {
		JSONObject jparam = new JSONObject();
		String index = request.param("index", ES_DICTIONARY_INDEX);
		String host = request.param("host", null);
		int port = request.paramAsInt("port", 9200);
		String username = request.param("username", null);
		String password = request.param("password", null);

		if (!GET.equals(request.method())) {
			jparam = parseRequestBody(request);
			index = jparam.optString("index", ES_DICTIONARY_INDEX);
			host = jparam.optString("host", null);
			port = jparam.optInt("port", 9200);
			username = jparam.optString("username", null);
			password = jparam.optString("password", null);
		}

		// .dsearch_dict_apply 인덱스에서 데이터를 Map형태로 가져온다.
		Map<String, Object> searchMap = SearchUtil.searchData(client, ".dsearch_dict_apply");


		RemoteNodeClient remoteNodeClient = new RemoteNodeClient(client.settings(), client.threadPool(), ES_DICTIONARY_INDEX, host, port, username, password);

		ProductNameDictionary productNameDictionary = getDictionary();
		builder
			.key("dictionary").array()
				.object()
				.key(ES_DICT_FIELD_TYPE).value("SYSTEM")
				.key("class").value(TagProbDictionary.class.getSimpleName())
				.key("count").value(productNameDictionary.size())
				.key("label").value(productNameDictionary.label())
				.key("seq").value(productNameDictionary.seq())
				.key("ignoreCase").value(productNameDictionary.ignoreCase())
				.key("tokenType").value("NONE") // system 사전은 없음.
				.endObject();
		Map<String, SourceDictionary<?>> dictionaryMap = productNameDictionary.getDictionaryMap();
		Set<String> keySet = dictionaryMap.keySet();
		for (String key : keySet) {
			String type = key.toUpperCase();
			SourceDictionary<?> sourceDictionary = dictionaryMap.get(key);
			int[] info = ProductNameDictionary.getDictionaryInfo(sourceDictionary);
			long indexCount;
			if (host != null && !"".equals(host)) {
				indexCount = SearchUtil.count(remoteNodeClient, index, QueryBuilders.matchQuery(ES_DICT_FIELD_TYPE, type));
			} else {
				indexCount = SearchUtil.count(client, index, QueryBuilders.matchQuery(ES_DICT_FIELD_TYPE, type));
			}

			// SearchMap에서 데이터를 가져와서 각 변수에 넣어준다.
			String id = type.toLowerCase();
			int count = 0;
			String appliedTime = "";
			String updatedTime = "";

			if(searchMap.get(id) != null){
				// 적용된 단어 갯수
				count = (Integer) searchMap.get(id);
			}
			if(searchMap.get(id + "_appliedTime") != null){
				// 사전이 적용된 시간
				appliedTime = (String) searchMap.get(id + "_appliedTime");
			}
			if(searchMap.get(id + "_updatedTime") != null){
				// 사전 데이터가 수정된 시간
				updatedTime = (String) searchMap.get(id + "_updatedTime");
			}

			builder.object()
				.key(ES_DICT_FIELD_TYPE).value(type)
				.key("class").value(sourceDictionary.getClass().getSimpleName())
				.key("dictType").value(sourceDictionary.type().name())
//				.key("count").value(info[0])
				.key("ignoreCase").value(sourceDictionary.ignoreCase())
				.key("tokenType").value(sourceDictionary.tokenType() == null ? "NONE" : sourceDictionary.tokenType())
				.key("label").value(sourceDictionary.label())
				.key("seq").value(sourceDictionary.seq())
				.key("id").value(key)
				.key("updatedTime").value(updatedTime) // 사전에 데이터가 추가/삭제/변경 된 시간
				.key("appliedTime").value(appliedTime) // 적용된 시간
				.key("count").value(count) // 적용 단어 갯수
				.key("indexCount").value(indexCount); // 작업 단어 갯수

			if (info[1] != 0) {
				builder.key("words").value(info[1]);
			}
			builder.endObject();
		}
		builder.endArray();
	}

	/**
	 * 사전내 단어 검색
	 */
	private void findDictionary(RestRequest request, NodeClient client, JSONWriter builder) {
		JSONObject jparam = new JSONObject();
		String index = request.param("index", ES_DICTIONARY_INDEX);
		String word = request.param("word", "");
		if (!GET.equals(request.method())) {
			jparam = parseRequestBody(request);
			index = jparam.optString("index", ES_DICTIONARY_INDEX);
			word = jparam.optString("word", "");
		}

		ProductNameDictionary productNameDictionary = getDictionary();
		Set<String> keySet = productNameDictionary.getDictionaryMap().keySet();

		builder
			.key("result").array();

		List<TagProb> tagProbs = productNameDictionary.find(CharVector.valueOf(word));
		for (int inx = 0;tagProbs != null && inx < tagProbs.size(); inx++) {
			TagProb tagProb = tagProbs.get(inx);
			builder.object()
				.key(ES_DICT_FIELD_TYPE).value("SYSTEM")
				.key(ES_DICT_FIELD_KEYWORD).value(word)
				.key("posTag").value(tagProb.posTag())
				.key("prob").value(tagProb.prob())
			.endObject();
		}
		for (String type : keySet) {
			type = type.toUpperCase();

			BoolQueryBuilder query = QueryBuilders.boolQuery()
				.must(QueryBuilders.matchQuery(ES_DICT_FIELD_TYPE, type))
				.must(QueryBuilders.boolQuery()
					.should(QueryBuilders.matchQuery(ES_DICT_FIELD_KEYWORD, word))
					.should(QueryBuilders.matchQuery(ES_DICT_FIELD_VALUE, word))
				);
			Iterator<Map<String, Object>> result = SearchUtil.search(client, index, query, null, null, 0, -1, true, null);
			while (result.hasNext()) {
				Map<String, Object> data = result.next();
				CharVector keyword = CharVector.valueOf(data.get(ES_DICT_FIELD_KEYWORD));
				CharVector value = CharVector.valueOf(data.get(ES_DICT_FIELD_VALUE));
				CharVector id = CharVector.valueOf(data.get(ES_DICT_FIELD_ID));
				builder.object();
				builder.key(ES_DICT_FIELD_TYPE).value(type);
				if (keyword != null && keyword.length() > 0) {
					builder.key(ES_DICT_FIELD_KEYWORD).value(keyword);
				}
				if (value != null && value.length() > 0) {
					builder.key(ES_DICT_FIELD_VALUE).value(value);
				}
				if (id != null && id.length() > 0) {
					builder.key(ES_DICT_FIELD_ID).value(id);
				}
				builder.endObject();
			}
		}
		builder.endArray();
	}

	/**
	 * 컴파일된 바이너리사전 -> ES 색인으로 데이터 복원
	 */
	private void restoreDictionary(RestRequest request, NodeClient client) {
		JSONObject jparam = new JSONObject();
		String index = request.param("index", ES_DICTIONARY_INDEX);
		if (!GET.equals(request.method())) {
			jparam = parseRequestBody(request);
			index = jparam.optString("index", ES_DICTIONARY_INDEX);
		}

		SearchUtil.deleteAllData(client, index);
		DictionarySource repo = new DictionarySource(client, index);
		ProductNameDictionary.restoreDictionary(repo, index);
	}

	/**
	 * 다나와 상품 텍스트데이터 -> ES색인 수행
	 */
	private int bulkIndex(final RestRequest request, final NodeClient client) {
		int ret = 0;
		JSONObject jparam = new JSONObject();
		String index = request.param("index", "");
		String path = request.param("path", "");
		String enc = request.param("enc", "euc-kr");
		int flush = request.paramAsInt("flush", 50000);
		if (!GET.equals(request.method())) {
			jparam = parseRequestBody(request);
			index = jparam.optString("index", "");
			path = jparam.optString("path", "");
			enc = jparam.optString("enc", "euc-kr");
			flush = jparam.optInt("flush", 50000);
		}

		synchronized (this) {
			if (indexingThread == null || !indexingThread.running()) {
				indexingThread = new DanawaBulkTextIndexer(index, path, enc, flush, client);
				indexingThread.start();
			} else {
				ret = indexingThread.count();
			}
		}
		return ret;
	}

	/**
	 * 다나와 상품 FASTCAT -> ES색인 수행
	 */
	private int fastcatIndex(final RestRequest request, final NodeClient client) {
		int ret = 0;
		JSONObject jparam = new JSONObject();
		String url = request.param("url", "");
		int start = request.paramAsInt("start", 1);
		int length = request.paramAsInt("length", 1000);
		String path = request.param("path", "");
		String enc = request.param("enc", "euc-kr");
		String index = request.param("index", "");
		int flush = request.paramAsInt("flush", 50000);
		try {
			url = URLDecoder.decode(url, "utf-8");
		} catch (Exception ignore) { }
		if (!GET.equals(request.method())) {
			jparam = parseRequestBody(request);
			url = jparam.optString("url", "");
			start = jparam.optInt("start", 1);
			length = jparam.optInt("length", 1000);
			path = jparam.optString("path", "");
			enc = jparam.optString("enc", "euc-kr");
			index = jparam.optString("index", "");
			flush = jparam.optInt("flush", 50000);
		}

		synchronized (this) {
			if (migratingThread == null || !migratingThread.running()) {
				migratingThread = new FastcatMigrateIndexer(url, start, length, path, enc, index, flush, client);
				migratingThread.start();
			} else {
				ret = migratingThread.count();
			}
		}
		return ret;
	}

	/**
	 * 상품명 분석기를 사용하여 검색 수행
	 */
	private void search(final RestRequest request, final NodeClient client, final JSONWriter builder) {
		JSONObject jparam = new JSONObject();
		String index = request.param("index", "");
		String[] fields = request.param("fields", "").split("[,]");
		Map<String, Float> boostMap = new HashMap<>();
		String text = request.param("text", "");
		String queryString = request.param("query", "");
		String totalIndex = request.param("totalIndex", ES_INDEX_TOTALINDEX);
		int from = request.paramAsInt("from", 0);
		int size = request.paramAsInt("size", 20);
		boolean showTotal = request.paramAsBoolean("showTotal", false);
		boolean showExplain = request.paramAsBoolean("showExplain", false);
		boolean showDetail = request.paramAsBoolean("showDetail", false);
		boolean useScroll = request.paramAsBoolean("useScroll", false);
		String sortStr = request.param("sort");
		String highlightStr = request.param("highlight");
		String analyzer = request.param("analyzer", "whitespace");
		if (!GET.equals(request.method())) {
			jparam = parseRequestBody(request);
			index = jparam.optString("index", "");
			fields = jparam.optString("fields", "").split("[,]");
			text = jparam.optString("text", "");
			queryString = jparam.optString("query", "");
			totalIndex = jparam.optString("totalIndex", ES_INDEX_TOTALINDEX);
			from = jparam.optInt("from", 0);
			size = jparam.optInt("size", 20);
			showTotal = jparam.optBoolean("showTotal", false);
			showExplain = jparam.optBoolean("showExplain", false);
			showDetail = jparam.optBoolean("showDetail", false);
			useScroll = jparam.optBoolean("useScroll", false);
			sortStr = jparam.optString("sort");
			highlightStr = jparam.optString("highlight");
			analyzer = jparam.optString("analyzer","whitespace");
		}

		for (int inx = 0; inx < fields.length; inx++) {
			String[] item = fields[inx].split("[\\^]");
			String field = item[0].trim();
			if (item.length > 1) {
				float boost = 1.0f;
				try {
					boost = Float.parseFloat(item[1].trim());
				} catch (Exception ignore) { }
				boostMap.put(field, boost);
			}
			fields[inx] = field;
		}

		TokenStream stream = null;
		try {
			logger.trace("ANALYZE TEXT : {}", text);
			stream = ProductNameAnalyzerProvider.getAnalyzer(text, true, true, true, true, false);
			JSONObject explain = null;
			if (showExplain) {
				explain = new JSONObject();
			}

			List<SortBuilder<?>> sortSet = null;
			if (sortStr != null && !"".equals(sortStr)) {
				logger.trace("SORT:{}", sortStr);
				sortSet = DanawaSearchQueryBuilder.parseSortSet(sortStr);
			}

			HighlightBuilder highlight = null;
			final List<String> views = new ArrayList<>();
			final List<String> highlightTags = new ArrayList<>();
			if (highlightStr != null && !"".equals(highlightStr)) {
				logger.trace("HIGHLIGHT:{}", highlightStr);
				highlight = DanawaSearchQueryBuilder.parseHighlight(highlightStr);
				String[] preTags = highlight.preTags();
				String[] postTags = highlight.postTags();
				if (preTags.length > 0 && postTags.length > 0) {
					highlightTags.add(preTags[0]);
					highlightTags.add(postTags[0]);
				}
				for (Field field : highlight.fields()) {
					String name = field.name();
					logger.trace("FIELD:{}", name);
					if (!"_all".equals(name)) {
						views.add(name);
					}
				}
			}

			final List<String> highlightTerms = new ArrayList<>();
			QueryBuilder query = DanawaSearchQueryBuilder.buildAnalyzedQuery(stream, fields, totalIndex, boostMap, views, highlightTerms, explain, analyzer);
			if (queryString != null && !"".equals(queryString)) {
				try {
					QueryBuilder baseQuery = DanawaSearchQueryBuilder.parseQuery(queryString);
					logger.trace("Q:{}", baseQuery);
					if (baseQuery instanceof FunctionScoreQueryBuilder) {
						QueryBuilder innerQuery = ((FunctionScoreQueryBuilder) baseQuery).query();
						if (innerQuery instanceof BoolQueryBuilder) {
							BoolQueryBuilder boolQuery = (BoolQueryBuilder) innerQuery;
							boolQuery.must().clear();
							boolQuery.must(query);
							logger.trace("Q:{}", boolQuery);
						}
						query = baseQuery;
					}
				} catch (Exception e) { 
					logger.error("", e);
				}
			}
			logger.trace("Q:{}", query);
			long total = -1;
			if (showTotal) {
				total = SearchUtil.count(client, index, query);
			}

			boolean doScroll = !useScroll ? false : from + size > 10000;
			builder.object();
			if (showExplain) {
				builder.key("explain").value(explain);
			}
			if (showDetail) {
				builder.key("detail").value(new JSONObject(String.valueOf(query)));
			}
			if (total != -1) {
				builder.key("total").value(total);
			}
			builder.key("result").array();

			DataModifier dataModifier = new DataModifier() {
				@Override public void modify(Map<String, Object> map) {
					for (String field : views) {
						if (map.containsKey(field)) {
							Object obj = map.get(field);
							if (obj instanceof String) {
								map.put(field, SearchUtil.highlightString(String.valueOf(obj), highlightTerms, highlightTags));
							}
						}
					}
				}
			};
			Iterator<Map<String, Object>> iter = SearchUtil.search(client, index, query, sortSet, highlight, from, size, doScroll, dataModifier);
			while (iter != null && iter.hasNext()) {
				Map<String, Object> map = iter.next();
				builder.object();
				for (String key : map.keySet()) {
					builder.key(key).value(map.get(key));
				}
				builder.endObject();
			}
			builder.endArray();
			builder.endObject();
		} catch (Exception e) {
			logger.error("", e);
		} finally {
			try { stream.close(); } catch (Exception ignore) { }
		}
	}

	/**
	 * 상품명 분석기를 사용하여 검색 질의어 생성
	 */
	private void buildQuery(final RestRequest request, final NodeClient client, final JSONWriter builder) {
		JSONObject jparam = new JSONObject();
		String[] fields = request.param("fields", "").split("[,]");
		String totalIndex = request.param("totalIndex", ES_INDEX_TOTALINDEX);
		String text = request.param("text", "");
		String analyzer = request.param("analyzer", "whitespace");
		if (!GET.equals(request.method())) {
			jparam = parseRequestBody(request);
			fields = jparam.optString("fields", "").split("[,]");
			totalIndex = jparam.optString("totalIndex", ES_INDEX_TOTALINDEX);
			text = jparam.optString("text", "");
			analyzer = jparam.optString("analyzer", "whitespace");
		}
		TokenStream stream = null;
		try {
			logger.trace("ANALYZE TEXT : {}", text);
			stream = ProductNameAnalyzerProvider.getAnalyzer(text, true, true, true, true, false);
			JSONObject query = DanawaSearchQueryBuilder.buildAnalyzedJSONQuery(stream, fields, totalIndex, analyzer);
			builder.object()
				.key("query").value(query)
			.endObject();
		} catch (Exception e) {
			logger.error("", e);
		} finally {
			try { stream.close(); } catch (Exception ignore) { }
		}
	}

	private void makeSearchKeyword(RestRequest request, NodeClient client, JSONWriter writer) {
		JSONObject jparam = new JSONObject();
		String index = request.param("index", ES_DICTIONARY_INDEX);
		String text = request.param("text", "");
		boolean stopWord = request.paramAsBoolean("stopWord", true);
		boolean synonym = request.paramAsBoolean("synonym", true);

		if (!GET.equals(request.method())) {
			jparam = parseRequestBody(request);
			index = jparam.optString("index", ES_DICTIONARY_INDEX);
			text = jparam.optString("text", "");
			stopWord = jparam.optBoolean("stopWord", true);
			synonym = jparam.optBoolean("synonym", true);
		}

		TokenStream stream = null;
		try {
			stream = ProductNameAnalyzerProvider.getAnalyzer(text, true, synonym, stopWord, true, false);
			expansionKeyword(text, stream, writer);
		} finally {
			try { stream.close(); } catch (Exception ignore) { }
		}
	}

	/**
	 * 상품명사전
	 */
	private static ProductNameDictionary getDictionary() {
		return contextStore.getAs(ProductNameDictionary.PRODUCT_NAME_DICTIONARY, ProductNameDictionary.class);
	}

	/**
	 * ES 사전 색인 조회용 클래스
	 * 사전 컴파일 등에 사용한다.
	 */
	public static class DictionarySource extends DictionaryRepository implements Iterator<CharSequence[]> {
		private Client client;
		private String index;
		private Iterator<Map<String, Object>> iterator;

		public DictionarySource(Client client, String index) {
			this.client = client;
			this.index = index;
		}

		@Override public Iterator<CharSequence[]> getSource(String type) {
			try {
				QueryBuilder query = null;

				query = QueryBuilders.matchQuery(ES_DICT_FIELD_TYPE, type.toUpperCase());

				logger.trace("QUERY:{}", query);
				iterator = SearchUtil.search(client, index, query, null, null, 0, -1, true, null);
			} catch (Exception e) {
				logger.error("", e);
			}
			return this;
		}

		@Override public void restore(String type, boolean ignoreCase, Set<CharSequence> wordSet) {
			Map<String, Object> source = null;
			BulkRequestBuilder builder = null;
			try {
				builder = client.prepareBulk();
				int inx = 0;
				for (CharSequence data : wordSet) {
					String[] words = String.valueOf(data).split(TAB);
					source = new HashMap<>();
					source.put(ES_DICT_FIELD_TYPE, type.toUpperCase());
					if (words.length == 1) {
						source.put(ES_DICT_FIELD_KEYWORD, words[0]);
					} else if (words.length == 2) {
						if (words[0].length() > 0) {
							source.put(ES_DICT_FIELD_KEYWORD, words[0]);
						}
						source.put(ES_DICT_FIELD_VALUE, words[1]);
					} else {
						source.put(ES_DICT_FIELD_KEYWORD, words[0]);
						source.put(ES_DICT_FIELD_VALUE, words[1]);
						source.put(ES_DICT_FIELD_ID, words[2]);
					}
					builder.add(client.prepareIndex(String.valueOf(index), "_doc").setSource(source));
					if (inx > 0 && inx % 1000 == 0) {
						builder.execute().actionGet();
						builder = client.prepareBulk();
					}
					inx++;
				}
				if (inx > 0) {
					builder.execute().actionGet();
				}
			} catch (Exception e) { 
				logger.error("", e);
			}
		}

		@Override public boolean hasNext() {
			return iterator.hasNext();
		}

		@Override public CharSequence[] next() {
			Map<String, Object> data = iterator.next();
			CharSequence id = CharVector.valueOf(data.get(ES_DICT_FIELD_ID));
			CharSequence keyword = CharVector.valueOf(data.get(ES_DICT_FIELD_KEYWORD));
			CharSequence value = CharVector.valueOf(data.get(ES_DICT_FIELD_VALUE));
			return new CharSequence[] { id, keyword, value };
		}

		@Override public void close() { }
	}

	private void getSynonymListAction(RestRequest request, NodeClient client, JSONWriter writer){
		JSONObject jparam = new JSONObject();
		String keyword = "";
		boolean useForQuery = true;
		boolean useSynonym = true;
		boolean useStopword = false;
		boolean useFullString = true;

		logger.debug("get-synonym-list start");
		if (!GET.equals(request.method())) {
			jparam = parseRequestBody(request);
			keyword = jparam.optString("keyword", "");
		} else {
			try{
				keyword = URLDecoder.decode(request.param("keyword", ""), "UTF-8");
			} catch (UnsupportedEncodingException e){
				logger.error("UnsupportedEncodingException Catched !!! >>>> \n" + e.getMessage());
				logger.debug("error >>> {}", e);
			}
		}

		TokenStream stream = null;
		try {
			writer.key("query").value(keyword);
			writer.key("result").array();
			stream = ProductNameAnalyzerProvider.getAnalyzer(keyword, useForQuery, useSynonym, useStopword, useFullString, false);
			getSynonymList(keyword, stream, writer);
			writer.endArray();
			writer.key("success").value(true);
		} finally {
			try {
				stream.close();
			} catch (Exception ignore) {
				logger.debug("Exception >>> {}", ignore.getMessage());
			}
		}
	}

	/**
	 * 동의어 리스트.
	 */
	public static void getSynonymList(String text, TokenStream stream, JSONWriter writer) {
		CharTermAttribute termAttr = stream.addAttribute(CharTermAttribute.class);
		TypeAttribute typeAttr = stream.addAttribute(TypeAttribute.class);
		SynonymAttribute synAttr = stream.addAttribute(SynonymAttribute.class);
		Map<String, List<List<String>>> result = new HashMap<>();
		List<CharSequence> synonyms = synAttr.getSynonyms();

		for (String key : ANALYSIS_RESULT_LABELS.keySet()) {
			result.put(key, new ArrayList<>());
		}

		String term = null;
		String type = null;
		String setName = null;
		try {
			stream.reset();
			while (stream.incrementToken()) {
				term = String.valueOf(termAttr);
				type = typeAttr.type();
				logger.trace("TERM:{} / {}", term, type);
				if (ProductNameTokenizer.FULL_STRING.equals(type)) {
					// 전체 단어
					setName = ANALYZE_SET_FULL_STRING;
					setAnalyzedResult(result, term, ANALYZE_SET_FULL_STRING, ANALYZE_SET_FINAL);
				} else if (ProductNameTokenizer.MODEL_NAME.equals(type)) {
					// 모델명의 경우 색인시와 질의시 추출방법이 서로 다르다.
					setName = ANALYZE_SET_MODEL_NAME;
					setAnalyzedResult(result, term, ANALYZE_SET_MODEL_NAME, ANALYZE_SET_FINAL);
				} else {
					// 일반단어
					setName = ANALYZE_SET_NORMAL;
					setAnalyzedResult(result, term, ANALYZE_SET_NORMAL, ANALYZE_SET_FINAL);
				}

				if (!ANALYZE_SET_FULL_STRING.equals(setName)) {
					if ((synonyms = synAttr.getSynonyms()) != null && synonyms.size() > 0) {
						if (term.equals(text)) {
							for (CharSequence synonym : synonyms) {
								String s = String.valueOf(synonym);
								writer.value(s);
							}
						}

					}
				}
			}
		} catch (Exception e) {
			logger.error("", e);
		}
	}
}