package org.bitbucket.eunjeon.elasticsearch.index;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.bitbucket.eunjeon.elasticsearch.product.analysis.ProductNameTokenizer;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.*;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.plugins.SearchPlugin;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import tokenattributes.ExtraTermAttribute;
import tokenattributes.SynonymAttribute;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DanawaSearchQueryBuilder {

	private static Logger logger = Loggers.getLogger(DanawaSearchQueryBuilder.class, "");

	public static final DanawaSearchQueryBuilder INSTANCE = new DanawaSearchQueryBuilder();

	public static final String AND = "AND";
	public static final String OR = "OR";
	public static final String SHOULD = "should";
	public static final String MUST = "must";
	public static final String BOOL = "bool";
	public static final String MATCHPHRASE = "match_phrase";
	public static final String MULTIMATCH = "multi_match";
	public static final String ANALYZER = "analyzer";
	public static final String QUERY = "query";
	public static final String FIELDS = "fields";
	public static final String SLOPE = "slope";
	public static final String WHITESPACE = "whitespace";

	public NamedXContentRegistry SEARCH_CONTENT_REGISTRY;

	public DanawaSearchQueryBuilder() {
		init();
	}

	private void registerQueryBuilder(SearchPlugin.QuerySpec<?> spec, List<NamedWriteableRegistry.Entry> namedWriteables, List<NamedXContentRegistry.Entry> namedXContents) {
		namedWriteables.add(new NamedWriteableRegistry
			.Entry(QueryBuilder.class, spec.getName().getPreferredName(), spec.getReader()));
		namedXContents.add(new NamedXContentRegistry
			.Entry(QueryBuilder.class, spec.getName(), (p, c) -> spec.getParser().fromXContent(p)));
	}

	public void init() {
		/**
		 * 파싱 가능한 객체들을 나열한다.
		 */
		final List<NamedWriteableRegistry.Entry> writables = new ArrayList<>();
		final List<NamedXContentRegistry.Entry> xcontents = new ArrayList<>();
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(MatchQueryBuilder.NAME, MatchQueryBuilder::new,
			MatchQueryBuilder::fromXContent), writables, xcontents);
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(MultiMatchQueryBuilder.NAME, MultiMatchQueryBuilder::new,
			MultiMatchQueryBuilder::fromXContent), writables, xcontents);
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(MatchPhraseQueryBuilder.NAME, MatchPhraseQueryBuilder::new,
			MatchPhraseQueryBuilder::fromXContent), writables, xcontents);
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(QueryStringQueryBuilder.NAME, QueryStringQueryBuilder::new,
			QueryStringQueryBuilder::fromXContent), writables, xcontents);
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(BoostingQueryBuilder.NAME, BoostingQueryBuilder::new,
			BoostingQueryBuilder::fromXContent), writables, xcontents);
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(BoolQueryBuilder.NAME, BoolQueryBuilder::new,
			BoolQueryBuilder::fromXContent), writables, xcontents);
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(RangeQueryBuilder.NAME, RangeQueryBuilder::new,
			RangeQueryBuilder::fromXContent), writables, xcontents);
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(WrapperQueryBuilder.NAME, WrapperQueryBuilder::new,
			WrapperQueryBuilder::fromXContent), writables, xcontents);
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(FunctionScoreQueryBuilder.NAME, FunctionScoreQueryBuilder::new,
			FunctionScoreQueryBuilder::fromXContent), writables, xcontents);
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(SimpleQueryStringBuilder.NAME, SimpleQueryStringBuilder::new,
			SimpleQueryStringBuilder::fromXContent), writables, xcontents);
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(TermsSetQueryBuilder.NAME, TermsSetQueryBuilder::new,
			TermsSetQueryBuilder::fromXContent), writables, xcontents);
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(TermQueryBuilder.NAME, TermQueryBuilder::new,
			TermQueryBuilder::fromXContent), writables, xcontents);
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(TermsQueryBuilder.NAME, TermsQueryBuilder::new,
			TermsQueryBuilder::fromXContent), writables, xcontents);
		SEARCH_CONTENT_REGISTRY = new NamedXContentRegistry(xcontents);
	}

	/**
	 * 질의문자열을 ES 질의객체로 생성한다.
	 * @param source
	 * @return
	 */
	public static QueryBuilder parseQuery(String source) {
		QueryBuilder ret = null;
		try {
			XContentParser parser = JsonXContent.jsonXContent
				.createParser(INSTANCE.SEARCH_CONTENT_REGISTRY, LoggingDeprecationHandler.INSTANCE, source);
			ret = AbstractQueryBuilder.parseInnerQueryBuilder(parser);
		} catch (Exception e) {
			logger.error("", e);
		}
		return ret;
	}

	/**
	 * 정렬문자열을 ES 정렬객체로 생성한다.
	 */
	public static List<SortBuilder<?>> parseSortSet(String source) {
		List<SortBuilder<?>> ret = null;
		try {
			XContentParser parser = JsonXContent.jsonXContent.createParser(NamedXContentRegistry.EMPTY,
				LoggingDeprecationHandler.INSTANCE, source);
			parser.nextToken();
			ret = SortBuilder.fromXContent(parser);
		} catch (Exception e) {
			logger.error("", e);
		}
		return ret;
	}

	/**
	 * 하이라이터문자열을 ES 정렬객체로 생성한다.
	 */
	public static HighlightBuilder parseHighlight(String source) {
		HighlightBuilder ret = null;
		try {
			XContentParser parser = JsonXContent.jsonXContent.createParser(NamedXContentRegistry.EMPTY,
				LoggingDeprecationHandler.INSTANCE, source);
			parser.nextToken();
			ret = HighlightBuilder.fromXContent(parser);
		} catch (Exception e) {
			logger.error("", e);
		}
		return ret;
	}

	/**
	 * 상품검색용 분석질의 객체 생성기, 분석기 속성 (동의어, 확장어 등) 을 고려한 질의객체를 생성한다.
	 */
	public static QueryBuilder buildAnalyzedQuery(TokenStream stream, String[] fields, String totalIndex, Map<String, Float> boostMap, List<String> views, List<String> highlightTerms, JSONObject explain, String analyzer) {
		QueryBuilder ret = null;
		CharTermAttribute termAttr = stream.addAttribute(CharTermAttribute.class);
		TypeAttribute typeAttr = stream.addAttribute(TypeAttribute.class);
		SynonymAttribute synAttr = stream.addAttribute(SynonymAttribute.class);
		ExtraTermAttribute extAttr = stream.addAttribute(ExtraTermAttribute.class);
		try {
			stream.reset();
			BoolQueryBuilder mainQuery = QueryBuilders.boolQuery();
			ret = mainQuery;
			JSONObject mainExplain = null;
			if (explain != null) {
				mainExplain = new JSONObject();
				mainExplain.put(AND, new JSONArray());
				explain.put(AND, mainExplain);
			}
			while (stream.incrementToken()) {
				String term = String.valueOf(termAttr);
				String type = typeAttr.type();
				logger.trace("TOKEN:{} / {}", term, type);
				if (highlightTerms != null) {
					highlightTerms.add(term);
				}

				JSONArray termExplain = null;
				if (explain != null) {
					termExplain = new JSONArray();
					termExplain.put(term);
				}
				List<CharSequence> synonyms = null;
				QueryBuilder termQuery = QueryBuilders.multiMatchQuery(term, fields).fields(boostMap).analyzer(analyzer);

				if (synAttr != null && (synonyms = synAttr.getSynonyms()) != null && synonyms.size() > 0) {
					logger.trace("SYNONYM [{}] = {}", term, synonyms);
					termQuery = synonymQuery(termQuery, synonyms, fields, boostMap, termAttr, typeAttr, synAttr, extAttr, highlightTerms, termExplain, analyzer);
				}
				if (extAttr != null && extAttr.size() > 0) {
					logger.trace("EXTRA [{}] = {}", term, extAttr);
					termQuery = extraQuery(termQuery, fields, boostMap, termAttr, typeAttr, synAttr, extAttr, highlightTerms, termExplain, analyzer);
				}

				if (!ProductNameTokenizer.FULL_STRING.equals(type)) {
					mainQuery.must().add(termQuery);
					if (explain != null) {
						if (termExplain.length() > 1) {
							JSONObject subExplain = new JSONObject();
							subExplain.put(OR, termExplain);
							mainExplain.optJSONArray(AND).put(subExplain);
						} else {
							mainExplain.optJSONArray(AND).put(term);
						}
					}
				}



			}
			// NOTE: ES 하이라이터의 부실로 커스텀 하이라이터를 사용예정
			// if (views != null && views.length > 0) {
			// 	ret = andQuery(ret, QueryBuilders.boolQuery()
			// 		.should(QueryBuilders.multiMatchQuery(String.valueOf(highlightTerms).trim(), views)));
			// }
		} catch (Exception e) {
			logger.error("", e);
		} finally {
			try { stream.close(); } catch (Exception ignore) { }
		}
		return ret;
	}

	/**
	 * 상품검색용 분석질의 객체 생성기, 분석기 속성 (동의어, 확장어 등) 을 고려한 질의 JSON문자열을 생성한다.
	 */
	public static JSONObject buildAnalyzedJSONQuery(TokenStream stream, String[] fields, String totalIndex, String analyzer) {
		JSONObject ret = new JSONObject();
		CharTermAttribute termAttr = stream.addAttribute(CharTermAttribute.class);
		TypeAttribute typeAttr = stream.addAttribute(TypeAttribute.class);
		SynonymAttribute synAttr = stream.addAttribute(SynonymAttribute.class);
		ExtraTermAttribute extAttr = stream.addAttribute(ExtraTermAttribute.class);
		try {
			stream.reset();
			JSONObject mainQuery = new JSONObject();
			ret = mainQuery;
			while (stream.incrementToken()) {
				String term = String.valueOf(termAttr);
				String type = typeAttr.type();
				logger.trace("TOKEN:{} / {}", term, type);

				List<CharSequence> synonyms = null;
				JSONObject termQuery = new JSONObject();
				termQuery.put(MULTIMATCH, 
					new JSONObject().put(ANALYZER, analyzer)
						.put(QUERY, term)
						.put(FIELDS, fields)
					);

				if (synAttr != null && (synonyms = synAttr.getSynonyms()) != null && synonyms.size() > 0) {
					logger.trace("SYNONYM [{}] = {}", term, synonyms);
					termQuery = synonymJSONQuery(termQuery, synonyms, fields, termAttr, typeAttr, synAttr, extAttr, analyzer);
				}
				if (extAttr != null && extAttr.size() > 0) {
					logger.trace("EXTRA [{}] = {}", term, extAttr);
					termQuery = extraJSONQuery(termQuery, fields, termAttr, typeAttr, synAttr, extAttr, analyzer);
				}

				if (!ProductNameTokenizer.FULL_STRING.equals(type)) {
					appendJSONQuery(mainQuery, MUST, termQuery);
				}

			}
		} catch (Exception e) {
			logger.error("", e);
		} finally {
			try { stream.close(); } catch (Exception ignore) { }
		}
		return ret;
	}

	private static void appendJSONQuery(JSONObject mainQuery, String mode, JSONObject termQuery) {
		JSONObject boolQuery = null;
		if (mainQuery.has(BOOL)) {
			boolQuery = mainQuery.getJSONObject(BOOL);
		} else {
			boolQuery = new JSONObject();
			mainQuery.put(BOOL, boolQuery);
		}
		JSONArray termQueries = null;
		if (boolQuery.has(mode)) {
			termQueries = boolQuery.getJSONArray(mode);
		} else {
			termQueries = new JSONArray();
			boolQuery.put(mode, termQueries);
		}
		termQueries.put(termQuery);
	}

	/**
	 * 전체 문장검색 질의객체 생성
	 */
	public final static QueryBuilder fullTermQuery(String totalIndex, Map<String, Float> boostMap, String phrase, int slop) {
		BoolQueryBuilder ret = QueryBuilders.boolQuery();
		ret.should().add(QueryBuilders.matchPhraseQuery(totalIndex, phrase).boost(0).slop(slop).analyzer(WHITESPACE));
		return ret;
	}

	/**
	 * 문장검색 질의객체 생성
	 */
	public final static QueryBuilder phraseQuery(String[] fields, Map<String, Float> boostMap, String phrase, int slop, String analyzer) {
		BoolQueryBuilder ret = QueryBuilders.boolQuery();
		String[] split = phrase.split(" ");
		for (String word : split) {
			ret.must().add(QueryBuilders.multiMatchQuery(word, fields).fields(boostMap).analyzer(analyzer));
		}
		return ret;
	}

	/**
	 * 문장검색 질의객체 생성
	 */
	public final static JSONObject phraseJSONQuery(String[] fields, String phrase, int slop, String analyzer) {
		JSONObject ret = new JSONObject();
		String[] split = phrase.split(" ");
		for (String word : split) {
			JSONObject termQuery = new JSONObject();
			termQuery.put(MULTIMATCH, 
				new JSONObject().put(ANALYZER, analyzer)
					.put(QUERY, word)
					.put(FIELDS, fields)
				);
			appendJSONQuery(ret, MUST, termQuery);
		}
		return ret;
	}

	/**
	 * 동의어 질의객체 생성
	 */
	public final static QueryBuilder synonymQuery(QueryBuilder query, List<CharSequence> synonyms, String[] fields, Map<String, Float> boostMap, 
		CharTermAttribute termAttr, TypeAttribute typeAttr, SynonymAttribute synAttr, 
		ExtraTermAttribute extAttr, List<String> highlightTerms, JSONArray explain, String analyzer) {
		QueryBuilder ret = query;
		BoolQueryBuilder subQuery = QueryBuilders.boolQuery();
		subQuery.should().add(query);
		for (int sinx = 0; sinx < synonyms.size(); sinx++) {
			String synonym = String.valueOf(synonyms.get(sinx));
			if (explain != null) {
				explain.put(synonym);
			}
			if (synonym.indexOf(" ") == -1) {
				subQuery.should().add(QueryBuilders.multiMatchQuery(synonym, fields).fields(boostMap).analyzer(analyzer));
			} else {
				/**
				 * 공백이 포함된 단어는 문장검색으로 질의
				 */
				subQuery.should().add(phraseQuery(fields, boostMap, synonym, 3, analyzer));
			}
		}
		ret = subQuery;
		return ret;
	}

	/**
	 * 동의어 JSON질의 문자열을 생성한다.
	 */
	public final static JSONObject synonymJSONQuery(JSONObject query, List<CharSequence> synonyms, String[] fields,
		CharTermAttribute termAttr, TypeAttribute typeAttr, SynonymAttribute synAttr, 
		ExtraTermAttribute extAttr, String analyzer) {
		JSONObject ret = query;
		JSONObject subQuery = new JSONObject();
		appendJSONQuery(subQuery, SHOULD, query);
		for (int sinx = 0; sinx < synonyms.size(); sinx++) {
			String synonym = String.valueOf(synonyms.get(sinx));
			if (synonym.indexOf(" ") == -1) {
				JSONObject termQuery = new JSONObject();
				termQuery.put(MULTIMATCH, 
					new JSONObject().put(ANALYZER, analyzer)
						.put(QUERY, synonym)
						.put(FIELDS, fields)
					);
				appendJSONQuery(subQuery, SHOULD, termQuery);
			} else {
				/**
				 * 공백이 포함된 단어는 문장검색으로 질의
				 */
				appendJSONQuery(subQuery, SHOULD, phraseJSONQuery(fields, synonym, 3, analyzer));
			}
		}
		ret = subQuery;
		return ret;
	}

	/**
	 * 확장어 질의객체 생성
	 */
	public final static QueryBuilder extraQuery(QueryBuilder query, String[] fields, Map<String, Float> boostMap, 
		CharTermAttribute termAttr, TypeAttribute typeAttr, SynonymAttribute synAttr, 
		ExtraTermAttribute extAttr, List<String> highlightTerms, JSONArray explain, String analyzer) {
		List<CharSequence> synonyms = null;
		QueryBuilder ret = query;
		if (extAttr != null && extAttr.size() > 0) {
			JSONArray termExplain = null;
			if (explain != null) {
				termExplain = new JSONArray();
				explain.put(new JSONObject().put(AND, termExplain));
			}
			BoolQueryBuilder subQuery = QueryBuilders.boolQuery();
			Iterator<String> termIter = extAttr.iterator();
			for (; termIter.hasNext();) {
				String term = termIter.next();
				String type = typeAttr.type();
				if (highlightTerms != null) {
					highlightTerms.add(term);
				}
				synonyms = synAttr.getSynonyms();
				if (synonyms == null || synonyms.size() == 0) {
					subQuery.must().add(QueryBuilders.multiMatchQuery(term, fields).fields(boostMap).analyzer(analyzer));
					if (explain != null) {
						termExplain.put(term);
					}
				} else {
					/**
					 * 확장어의 동의어가 존재할때
					 */
					JSONArray subExplain = new JSONArray();
					subExplain.put(term);
					termExplain.put(new JSONObject().put(OR, subExplain));
					MultiMatchQueryBuilder termQuery = QueryBuilders.multiMatchQuery(term, fields);
					subQuery.must().add(synonymQuery(termQuery, synonyms, fields, boostMap, termAttr, typeAttr, synAttr, extAttr, highlightTerms, subExplain,analyzer));
				}
				logger.trace("a-term:{} / type:{} / synonoym:{}", term, type, synonyms);
			}
			BoolQueryBuilder parent = QueryBuilders.boolQuery();
			parent.should().add(query);
			parent.should().add(subQuery);
			ret = parent;
		}
		return ret;
	}

	/**
	 * 확장어 질의 문자열 객체 생성
	 */
	public final static JSONObject extraJSONQuery(JSONObject query, String[] fields,
		CharTermAttribute termAttr, TypeAttribute typeAttr, SynonymAttribute synAttr, 
		ExtraTermAttribute extAttr, String analyzer) {
		List<CharSequence> synonyms = null;
		JSONObject ret = query;
		if (extAttr != null && extAttr.size() > 0) {
			JSONObject subQuery = new JSONObject();
			Iterator<String> termIter = extAttr.iterator();
			for (; termIter.hasNext();) {
				String term = termIter.next();
				String type = typeAttr.type();
				synonyms = synAttr.getSynonyms();
				JSONObject termQuery = new JSONObject();
				termQuery.put(MULTIMATCH, 
					new JSONObject().put(ANALYZER, analyzer)
						.put(QUERY, term)
						.put(FIELDS, fields)
					);

				if (synonyms == null || synonyms.size() == 0) {
					appendJSONQuery(subQuery, MUST, termQuery);
				} else {
					/**
					 * 확장어의 동의어가 존재할때
					 */
					appendJSONQuery(subQuery, MUST, synonymJSONQuery(termQuery, synonyms, fields, termAttr, typeAttr, synAttr, extAttr, analyzer));
				}
				logger.trace("a-term:{} / type:{} / synonoym:{}", term, type, synonyms);
			}
			JSONObject parent = new JSONObject();
			appendJSONQuery(parent, SHOULD, subQuery);
			appendJSONQuery(parent, SHOULD, query);
			ret = parent;
		}
		return ret;
	}

	/**
	 * AND 조건 불리언 질의객체 생성
	 */
	public static QueryBuilder andQuery(QueryBuilder... queries) {
		BoolQueryBuilder ret = QueryBuilders.boolQuery();
		for (QueryBuilder query : queries) {
			ret.must(query);
		}
		return ret;
	}

	/**
	 * OR 조건 불리언 질의객체 생성
	 */
	public static QueryBuilder orQuery(QueryBuilder... queries) {
		BoolQueryBuilder ret = QueryBuilders.boolQuery();
		for (QueryBuilder query : queries) {
			ret.should(query);
		}
		return ret;
	}
}