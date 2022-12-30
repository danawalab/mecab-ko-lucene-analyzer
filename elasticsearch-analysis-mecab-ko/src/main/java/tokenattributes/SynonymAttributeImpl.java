package tokenattributes;

import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.AttributeReflector;

import java.util.List;

public class SynonymAttributeImpl extends AttributeImpl implements SynonymAttribute {
	private List<CharSequence> synonyms;

	public SynonymAttributeImpl() { }

	public SynonymAttributeImpl(List<CharSequence> synonyms) {
		this.synonyms = synonyms;
	}

	@Override
	public void clear() {
		synonyms = null;
	}

	@Override
	public boolean equals(Object other) {
		if (other == this) {
			return true;
		}
		if (other instanceof SynonymAttributeImpl) {
			final SynonymAttributeImpl o = (SynonymAttributeImpl) other;
			return (this.synonyms == null ? o.synonyms == null : this.synonyms.equals(o.synonyms));
		}
		return false;
	}

	@Override
	public int hashCode() {
		return (synonyms == null) ? 0 : synonyms.hashCode();
	}

	@Override
	public void copyTo(AttributeImpl target) {
		SynonymAttribute t = (SynonymAttribute) target;
		t.setSynonyms(synonyms);
	}

	@Override
	public void setSynonyms(List<CharSequence> synonyms) {
		if (synonyms == null || synonyms.size() == 0) {
			this.synonyms = null;
		} else {
			this.synonyms = synonyms;
		}
	}

	@Override
	public List<CharSequence> getSynonyms() {
		if (synonyms == null || synonyms.size() == 0) {
			return null;
		}
		return synonyms;
	}

	@Override
	public String toString() {
		return String.valueOf(synonyms);
	}

	@Override
	public void reflectWith(AttributeReflector reflector) {
		reflector.reflect(SynonymAttribute.class, "synonyms", synonyms);
	}
}