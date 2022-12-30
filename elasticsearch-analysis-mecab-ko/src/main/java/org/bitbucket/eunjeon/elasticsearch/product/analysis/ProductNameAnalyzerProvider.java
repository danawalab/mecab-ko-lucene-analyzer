package org.bitbucket.eunjeon.elasticsearch.product.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.bitbucket.eunjeon.elasticsearch.dict.analysis.ProductNameDictionary;
import org.bitbucket.eunjeon.elasticsearch.index.analysis.MeCabKoKeywordSearchTokenizerFactory;
import org.bitbucket.eunjeon.elasticsearch.index.analysis.MeCabKoSimilarityMeasureTokenizerFactory;
import org.bitbucket.eunjeon.elasticsearch.index.analysis.MeCabKoStandardTokenizerFactory;
import org.bitbucket.eunjeon.elasticsearch.plugin.analysis.AnalysisMeCabKoStandardPlugin;
import org.bitbucket.eunjeon.elasticsearch.util.ContextStore;
import org.bitbucket.eunjeon.mecab_ko_lucene_analyzer.MeCabKoTokenizer;
import org.bitbucket.eunjeon.mecab_ko_lucene_analyzer.StandardPosAppender;
import org.bitbucket.eunjeon.mecab_ko_lucene_analyzer.TokenizerOption;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractIndexAnalyzerProvider;
import org.elasticsearch.index.analysis.TokenizerFactory;
import org.elasticsearch.indices.analysis.AnalysisModule;

import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import static org.apache.zookeeper.ZooDefs.OpCode.create;

public class ProductNameAnalyzerProvider extends AbstractIndexAnalyzerProvider<Analyzer> {
    private static final ContextStore contextStore = ContextStore.getStore(AnalysisMeCabKoStandardPlugin.class);
    private static ProductNameDictionary dictionary;
    private Analyzer analyzer;

    public ProductNameAnalyzerProvider(IndexSettings indexSettings, Environment env, String name, Settings settings) {
        super(indexSettings, name, settings);
        logger.trace("ProductNameAnalyzerProvider::self {}", this);
        if (contextStore.containsKey(ProductNameDictionary.PRODUCT_NAME_DICTIONARY)) {
            dictionary = contextStore.getAs(ProductNameDictionary.PRODUCT_NAME_DICTIONARY, ProductNameDictionary.class);
        } else {
            dictionary = ProductNameDictionary.loadDictionary(env);
            contextStore.put(ProductNameDictionary.PRODUCT_NAME_DICTIONARY, dictionary);
        }
        analyzer = new ProductNameAnalyzer(dictionary);
    }

    @Override
    public Analyzer get() {
        logger.trace("ProductNameAnalyzerProvider::get {}", analyzer);
        return analyzer;
    }

    public static TokenStream getAnalyzer(String str, boolean useForQuery, boolean useSynonym, boolean useStopword, boolean useFullString, boolean toUppercase) {
        TokenStream tstream = null;
        Reader reader = null;
        Tokenizer tokenizer = null;
        AnalyzerOption option = null;
        TokenizerOption tokenizerOption = new TokenizerOption();
        option = new AnalyzerOption(useForQuery, useSynonym, useStopword, useFullString, toUppercase);
        reader = new StringReader(str);

        // Mecab 토크나이저 사용.. tokenizer = new ProductNameTokenizer(dictionary, false);
        tokenizer = new MeCabKoTokenizer(tokenizerOption, new StandardPosAppender(tokenizerOption));
        tokenizer.setReader(reader);

        tstream = new ProductNameAnalysisFilter(tokenizer, dictionary, option);
        return tstream;
    }
}
