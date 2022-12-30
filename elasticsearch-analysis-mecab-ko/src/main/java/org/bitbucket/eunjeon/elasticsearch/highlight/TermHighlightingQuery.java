package org.bitbucket.eunjeon.elasticsearch.highlight;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.PrefixCodedTerms;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.PrefixCodedTerms.TermIterator;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.SortedSet;

public class TermHighlightingQuery extends Query implements Accountable {
	static final int BOOLEAN_REWRITE_TERM_COUNT_THRESHOLD = 16;
	private final PrefixCodedTerms termData;

	public TermHighlightingQuery(String field, Collection<BytesRef> terms) {
		BytesRef[] sortedTerms = terms.toArray(new BytesRef[0]);
		boolean sorted = terms instanceof SortedSet && ((SortedSet<BytesRef>) terms).comparator() == null;
		if (!sorted) {
			ArrayUtil.timSort(sortedTerms);
		}
		PrefixCodedTerms.Builder builder = new PrefixCodedTerms.Builder();
		BytesRefBuilder previous = null;
		for (BytesRef term : sortedTerms) {
			if (previous == null) {
				previous = new BytesRefBuilder();
			} else if (previous.get().equals(term)) {
				continue; // deduplicate
			}
			builder.add(field, term);
			previous.copyBytes(term);
		}
		termData = builder.finish();
	}

	public TermHighlightingQuery(String field, BytesRef... terms) {
		this(field, Arrays.asList(terms));
	}

	@Override public Query rewrite(IndexReader reader) throws IOException {
		final int threshold = Math.min(BOOLEAN_REWRITE_TERM_COUNT_THRESHOLD, BooleanQuery.getMaxClauseCount());
		if (termData.size() <= threshold) {
			BooleanQuery.Builder bq = new BooleanQuery.Builder();
			TermIterator iterator = termData.iterator();
			for (BytesRef term = iterator.next(); term != null; term = iterator.next()) {
				bq.add(new TermQuery(new Term(iterator.field(), BytesRef.deepCopyOf(term))), Occur.SHOULD);
			}
			return new ConstantScoreQuery(bq.build());
		}
		return super.rewrite(reader);
	}
	@Override public void visit(QueryVisitor visitor) { }
	@Override public boolean equals(Object other) { return false; }
	@Override public int hashCode() { return 0; }
	@Override public String toString(String defaultField) { return null; }
	@Override public long ramBytesUsed() { return 0; }
	@Override public Collection<Accountable> getChildResources() { return null; }
	@Override public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException { return null; }
}