package net.sandius.rembulan.compiler.gen;

import net.sandius.rembulan.util.Check;
import net.sandius.rembulan.util.PartialOrderComparisonResult;
import net.sandius.rembulan.util.ReadOnlyArray;

import java.util.ArrayList;

public class TypeSeq implements GradualTypeLike<TypeSeq> {

	public final ReadOnlyArray<Type> fixed;
	public final boolean varargs;

	public TypeSeq(ReadOnlyArray<Type> fixed, boolean varargs) {
		Check.notNull(fixed);
		this.fixed = fixed;
		this.varargs = varargs;
	}

	private static final TypeSeq EMPTY_FIXED = new TypeSeq(ReadOnlyArray.wrap(new Type[0]), false);
	private static final TypeSeq EMPTY_VARARG = new TypeSeq(ReadOnlyArray.wrap(new Type[0]), true);

	public static TypeSeq empty() {
		return EMPTY_FIXED;
	}

	public static TypeSeq vararg() {
		return EMPTY_VARARG;
	}

	public static TypeSeq of(Type... fixed) {
		return new TypeSeq(ReadOnlyArray.wrap(fixed), false);
	}

	public TypeSeq withVararg() {
		return hasVarargs() ? this : new TypeSeq(fixed, true);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		TypeSeq that = (TypeSeq) o;

		if (varargs != that.varargs) return false;
		return fixed.equals(that.fixed);
	}

	@Override
	public int hashCode() {
		int result = fixed.shallowHashCode();
		result = 31 * result + (varargs ? 1 : 0);
		return result;
	}

	@Override
	public String toString() {
		StringBuilder bld = new StringBuilder();
		for (int i = 0; i < fixed.size(); i++) {
			bld.append(Type.toString(fixed.get(i)));
		}
		if (varargs) {
			bld.append("+");
		}
		return bld.toString();
	}

	public ReadOnlyArray<Type> fixed() {
		return fixed;
	}

	public boolean hasVarargs() {
		return varargs;
	}

	public boolean isVarargOnly() {
		return fixed().isEmpty() && hasVarargs();
	}

	public Type get(int idx) {
		Check.nonNegative(idx);

		if (idx < fixed().size()) return fixed().get(idx);  // it's a fixed arg
		else if (hasVarargs()) return Type.ANY;  // it's a vararg
		else return Type.NIL;  // it's not there
	}

	public boolean isSubsumedBy(TypeSeq that) {
		Check.notNull(that);

		// that is more general than this

		for (int i = 0; i < Math.max(this.fixed().size(), that.fixed().size()); i++) {
			if (!this.get(i).isSubtypeOf(that.get(i))) {
				return false;
			}
		}

		return that.hasVarargs() || !this.hasVarargs();
	}

	public TypeSeq join(TypeSeq that) {
		Check.notNull(that);

		ArrayList<Type> fix = new ArrayList<>();

		for (int i = 0; i < Math.max(this.fixed().size(), that.fixed().size()); i++) {
			fix.add(this.get(i).join(that.get(i)));
		}

		return new TypeSeq(ReadOnlyArray.fromCollection(Type.class, fix), this.hasVarargs() || that.hasVarargs());
	}

	// returns null to indicate that no meet exists
	public TypeSeq meet(TypeSeq that) {
		Check.notNull(that);

		ArrayList<Type> fix = new ArrayList<>();

		for (int i = 0; i < Math.max(this.fixed().size(), that.fixed().size()); i++) {
			Type m = this.get(i).meet(that.get(i));
			if (m != null) {
				fix.add(m);
			}
			else {
				return null;
			}
		}

		return new TypeSeq(ReadOnlyArray.fromCollection(Type.class, fix), this.hasVarargs() && that.hasVarargs());
	}

	// Let < be the subtyping relation, and if seq is a TypeSeq and i is an index (a non-negative
	// integer), seq[i] is a shortcut for seq.get(i). We'll say that a type u is comparable
	// to type v iff (u == v || u < v || v < u), and not comparable otherwise.
	//
	// Then:
	// Returns NOT_COMPARABLE iff there is an index i such that this[i] is not comparable to that[i];
	// Returns EQUAL iff for all i, this[i] == that[i];
	// Returns LESSER_THAN iff there is an index i such that for all j < i, this[j] == that[j]
	//   and this[i] < that[i] and for all k, this[k] is comparable to that[k];
	// Returns GREATER_THAN iff there is an index i such that for all j < i, this[j] == that[j]
	//   and that[i] < this[i] and for all k, this[k] is comparable to that[k].
	//
	// If this.isSubsumedBy(that) then this.comparePointwiseTo(that) == EQUAL || this.comparePointwiseTo(that) == LESSER_THAN;
	// Please note that this is an implication: the opposite direction does *not* in general hold.
	public PartialOrderComparisonResult comparePointwiseTo(TypeSeq that) {
		Check.notNull(that);

		int len = Math.max(this.fixed().size(), that.fixed().size());

		PartialOrderComparisonResult result = null;

		for (int i = 0; i < len; i++) {
			PartialOrderComparisonResult r = this.get(i).compareTo(that.get(i));

			if (!r.isDefined()) {
				return PartialOrderComparisonResult.NOT_COMPARABLE;
			}

			if (result == null && r != PartialOrderComparisonResult.EQUAL) {
				result = r;
			}
		}

		if (result != null) {
			return result;
		}

		if (this.hasVarargs() && !that.hasVarargs()) return PartialOrderComparisonResult.GREATER_THAN;
		else if (!this.hasVarargs() && that.hasVarargs()) return PartialOrderComparisonResult.LESSER_THAN;
		else return PartialOrderComparisonResult.EQUAL;
	}

	@Override
	public boolean isConsistentWith(TypeSeq that) {
		Check.notNull(that);

		for (int i = 0; i < Math.max(this.fixed().size(), that.fixed().size()); i++) {
			if (!this.get(i).isConsistentWith(that.get(i))) {
				return false;
			}
		}

		return this.hasVarargs() == that.hasVarargs();
	}

	@Override
	public boolean isConsistentSubtypeOf(TypeSeq that) {
		Check.notNull(that);

		for (int i = 0; i < Math.max(this.fixed().size(), that.fixed().size()); i++) {
			if (!this.get(i).isConsistentSubtypeOf(that.get(i))) {
				return false;
			}
		}

		return that.hasVarargs() || !this.hasVarargs();
	}

}
