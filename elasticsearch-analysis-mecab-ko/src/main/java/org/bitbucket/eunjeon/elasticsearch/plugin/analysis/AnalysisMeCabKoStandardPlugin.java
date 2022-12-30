package org.bitbucket.eunjeon.elasticsearch.plugin.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.bitbucket.eunjeon.elasticsearch.index.analysis.*;
import org.bitbucket.eunjeon.elasticsearch.product.analysis.ProductNameAnalyzerProvider;
import org.elasticsearch.index.analysis.AnalyzerProvider;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.index.analysis.TokenizerFactory;
import org.elasticsearch.indices.analysis.AnalysisModule;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;
import java.util.HashMap;
import java.util.Map;
import static java.util.Collections.singletonMap;

public class AnalysisMeCabKoStandardPlugin extends Plugin implements AnalysisPlugin {

  /**
   * 필터 등록.
   * ES 에서 product_name 이름으로 상품명 필터를 생성하여 사용할수 있도록 등록.
   */
  @Override public Map<String, AnalysisModule.AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
    Map<String, AnalysisModule.AnalysisProvider<TokenFilterFactory>> extra = new HashMap<>();
    extra.put("mecab_product_name", ProductNameAnalysisFilterFactory::new);
    return extra;
  }

//  @Override
//  public Map<String, AnalysisModule.AnalysisProvider<TokenizerFactory>> getTokenizers() {
//    Map<String, AnalysisModule.AnalysisProvider<TokenizerFactory>> tokenizers = new HashMap<>();
//
//    tokenizers.put("mecab_ko_standard_tokenizer", MeCabKoStandardTokenizerFactory::new);
//    tokenizers.put("mecab_ko_similarity_measure_tokenizer", MeCabKoSimilarityMeasureTokenizerFactory::new);
//    tokenizers.put("mecab_ko_keyword_search_tokenizer", MeCabKoKeywordSearchTokenizerFactory::new);
//
//    return tokenizers;
//  }

  /**
   * 분석기 등록.
   * ES 에서 product_name 이름으로 상품명분석기를 생성하여 사용할수 있도록 분석기를 등록
   */
  @Override public Map<String, AnalysisModule.AnalysisProvider<AnalyzerProvider<? extends Analyzer>>> getAnalyzers() {
    return singletonMap("mecab_product_name", ProductNameAnalyzerProvider::new);
  }
}