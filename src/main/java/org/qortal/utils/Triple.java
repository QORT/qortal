package org.qortal.utils;

public class Triple<A, B, C> {

	@FunctionalInterface
	public interface TripleConsumer<A, B, C> {
		public void accept(A a, B b, C c);
	}

	private A a;
	private B b;
	private C c;

	public static <A, B, C> Triple<A, B, C> valueOf(A a, B b, C c) {
		return new Triple<>(a, b, c);
	}

	public Triple() {
	}

	public Triple(A a, B b, C c) {
		this.a = a;
		this.b = b;
		this.c = c;
	}

	public void setA(A a) {
		this.a = a;
	}

	public A getA() {
		return a;
	}

	public void setB(B b) {
		this.b = b;
	}

	public B getB() {
		return b;
	}

	public void setC(C c) {
		this.c = c;
	}

	public C getC() {
		return c;
	}

	public void consume(TripleConsumer<A, B, C> consumer) {
		consumer.accept(this.a, this.b, this.c);
	}

}
