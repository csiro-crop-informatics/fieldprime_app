/*
 * Class that is a Bexp that compares a value to some node property
 * using one of the standard numeric comparisons (<,>,<=,>=,==,!=).
 * ..but how do we ensure property is numeric?
 */
package csiro.fieldprime;

import csiro.fieldprime.Trial.Node;
import csiro.fieldprime.Trial.NodeProperty;

public class BexpNumericComparisonUnary  extends Bexp {
	NodeProperty mRop;
	BexpNumericComparator mOp;

	public enum BexpNumericComparator {
		LT("<", "less than"),
		LE("<=", "less than or equal to"),
		GT(">", "greater than"),
		GE(">=", "greater than or equal to"),
		EQ("=", "equals"),
		NE("!=", "not equals");
		
		private String mShortDesc;
		private String mLongDesc;
		
		BexpNumericComparator(String shortDesc, String longDesc) {
			mShortDesc = shortDesc;
			mLongDesc = longDesc;
		}
		public String getLongDesc() {
			return mLongDesc;
		}
	}
	
	/*
	 * Constructor
	 * Private, outside world should use static factory func provided. 
	 */
	private BexpNumericComparisonUnary(NodeProperty rtup, BexpNumericComparator op) {
		this.mRop = rtup;
		this.mOp = op;
	}
	
	/*
	 * getDescription()
	 * Returns text description of the condition.
	 */
	String getDescription() {
		return "Score " + mOp.mShortDesc + " " + mRop.name();
	}
	
	@Override
	public boolean getValue(Node tu) {
		// This should not be called, we need a value..
		return false;
	}
	
	/*
	 * getValue()
	 * Returns the result of the comparison that this object represents
	 * between the property value of the specified Node and the passed value.
	 * 
	 * Note that although the type of val argument is double, it may be called
	 * with an int - which would cast the int to a double. Since the node
	 * value we compare it to is also forced to be a double (maybe again cast
	 * from an int), the comparison may be valid between ints and doubles.
	 */
	public boolean getValue(Node nd, double val) {
		Double nodeVal = mRop.valueNumeric(nd);
		if (nodeVal == null) {
			// MFK why warning? probably should allow syntax in condition to specify whether
			// absence of value should return true or false. eg val < att && hasValue(att)
			Util.msg("Warning - Can't get attribute value for comparison");
			return true;
		}
		switch (mOp) {
		case LT: return val < nodeVal;
		case LE: return val <= nodeVal;
		case EQ: return val == nodeVal;
		case GT: return val > nodeVal;
		case GE: return val >= nodeVal;
		case NE: return val != nodeVal;
		default: return false;
		}
	}

	/*
	 * makeFromString()
	 * Construct from String of format:
	 *   ". <2_char_comparator_code> att:<attribute_id>"
	 * eg ". gt att:17"
	 * This is the only format supported ATTOW, but we could
	 * image more complicated ones. But note this type assumes
	 * we have a unary comparison (hence the dot in the string).
	 */
	static public BexpNumericComparisonUnary makeFromString(String vs, Trial trl) {
		String [] tokens = vs.split("\\s");
		if (tokens.length != 3) {
			Util.msg("Unexpected number of tokens (" + tokens.length + ") in trait validation (" + vs + ").");
			return null;
		}
		
		if (!tokens[0].equals(".")) {
			Util.msg("Unexpected first token (" + tokens[0] + ") in trait validation.");
			return null;
		}

		// Get the attribute we are comparing to:
		String atty = tokens[2];
		int alen = atty.length();
		NodeProperty tup;
		if (alen > 4) {
			String saId = atty.substring(4);
			long aid;
			try {
				aid = Integer.parseInt(saId);
			} catch (NumberFormatException e) {
				return null;
			}
			tup = trl.getAttribute(aid);
		} else {
			Util.msg("Invalid attribute token (" + tokens[2] + ") in trait validation.");
			return null;
		}
		
		BexpNumericComparator compOp;
		String sCompOp = tokens[1];
		if (sCompOp.equals("gt")) {
			compOp = BexpNumericComparator.GT;
		} else if (sCompOp.equals("ge")) {
			compOp = BexpNumericComparator.GE;
		} else if (sCompOp.equals("lt")) {
			compOp = BexpNumericComparator.LT;
		} else if (sCompOp.equals("le")) {
			compOp = BexpNumericComparator.LE;
		} else
			return null;
		
		return new BexpNumericComparisonUnary(tup, compOp);
		
//		StringTokenizer vst = new StringTokenizer(vs, "(),");
//		try {
//			return getOneExpression(vst);
//		} catch (NoSuchElementException e) {
//			Util.msg("missing element");
//			return null;
//		}

//		while (vst.hasMoreElements()) {
//			String t = vst.nextToken();
//			if (t.equals("and")) {
//				BexpAnd newNode = new BexpAnd();
//				try {
//					if (!vst.nextToken().equals("(")) {
//						Util.msg("missing (");
//						return null;
//					}
//					getOneExpression(vst)
//					
//					
//				} catch (NoSuchElementException e) {
//					
//				}
//				
//			}
//		}
//		return null;
	}
}
