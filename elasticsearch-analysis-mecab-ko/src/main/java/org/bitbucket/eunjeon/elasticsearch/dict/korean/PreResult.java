package org.bitbucket.eunjeon.elasticsearch.dict.korean;

/**
 * 기분석 리스트. 분리어와 기분석을 함께 처리가능.
 */
public class PreResult<T> {
	private T[] addition;
	private T[] result;

	public PreResult() {
	}

	public PreResult(T[] result, T[] addition) {
		this.result = result;
		this.addition = addition;
	}

	public T[] getAddition() {
		return addition;
	}

	public void setAddition(T[] addition) {
		this.addition = addition;
	}

	public T[] getResult() {
		return result;
	}

	public void setResult(T[] result) {
		this.result = result;
	}
}