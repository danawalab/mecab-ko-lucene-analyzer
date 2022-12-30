package org.bitbucket.eunjeon.elasticsearch.index.analysis;

import org.bitbucket.eunjeon.elasticsearch.product.analysis.ProductNameAnalysisFilter;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.bitbucket.eunjeon.elasticsearch.dict.analysis.ProductNameDictionary;
import org.bitbucket.eunjeon.elasticsearch.plugin.analysis.AnalysisMeCabKoStandardPlugin;
import org.bitbucket.eunjeon.elasticsearch.product.analysis.AnalyzerOption;
import org.bitbucket.eunjeon.elasticsearch.util.ContextStore;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractTokenFilterFactory;

public class ProductNameAnalysisFilterFactory extends AbstractTokenFilterFactory {

    private static Logger logger = Loggers.getLogger(ProductNameAnalysisFilterFactory.class, "");

    private static final ContextStore contextStore = ContextStore.getStore(AnalysisMeCabKoStandardPlugin.class);

    private ProductNameDictionary dictionary;
    private AnalyzerOption option;

    public ProductNameAnalysisFilterFactory(IndexSettings indexSettings, Environment env, String name, Settings settings) {
        super(indexSettings, name, settings);
        boolean useForQuery = settings.getAsBoolean("use_for_query", true);
        boolean useSynonym = settings.getAsBoolean("use_synonym", true);
        boolean useStopword = settings.getAsBoolean("use_stopword", true);
        boolean useFullString = settings.getAsBoolean("use_full_string", false);
        option = new AnalyzerOption(useForQuery, useSynonym, useStopword, useFullString, false);
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        logger.trace("ProductNameAnalysisFilter::create {}", this);
        if (contextStore.containsKey(ProductNameDictionary.PRODUCT_NAME_DICTIONARY)) {
            dictionary = contextStore.getAs(ProductNameDictionary.PRODUCT_NAME_DICTIONARY, ProductNameDictionary.class);
        }
        return new ProductNameAnalysisFilter(tokenStream, dictionary, option);
    }
}
