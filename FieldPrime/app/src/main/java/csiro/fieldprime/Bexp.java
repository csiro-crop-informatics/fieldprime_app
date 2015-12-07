/*
 * Bexp.java
 * Michael Kirk 2013.
 * Boolean expression class for TUProperties.
 */

package csiro.fieldprime;

import csiro.fieldprime.Trial.Node;
import csiro.fieldprime.Trial.NodeProperty;


abstract public class Bexp {
	private enum BexpNodeType {
		AND, OR, COMPARISON;
	}
	BexpNodeType mNodeType;
	
	abstract public boolean getValue(Node tu);

	static class BexpAnd extends Bexp {
		Bexp mLeft, mRight;  // left and right operands, should have array, for multi..
		public boolean getValue(Node tu) {
			return mLeft.getValue(tu) && mRight.getValue(tu);
		}
		public void setLeft(Bexp left) {
			mLeft = left;
		}
		public void setRight(Bexp right) {
			mRight = right;
		}
	}
	static class BexpOr extends Bexp {
		Bexp lop, rop;  // left and right operands
		public boolean getValue(Node tu) {
			return lop.getValue(tu) || rop.getValue(tu);
		}
	}
}


//### GRAVEYARD: #####################################################################################

//	static class BexpComparison extends Bexp {
//		Bexp lop, rop;  // left and right operands
//		BexpIntComparisonUnary.BexpIntComparator mOp;
//		
//		public boolean getValue(Node tu) {
//			return lop.getValue(tu) || rop.getValue(tu);
//		}
//	}

// Looks like code from some attempt to have tree structure expressions. Perhaps..
///*
//* getOneExpression()
//* read minimal complete expression, leaving vst
//* pointing at next token after that.
//*/
//private boolean check(StringTokenizer vst, char c) {
//	try {
//		if (!vst.nextToken().equals(c)) {
//			Util.msg("missing " + c);
//			return false;
//		}
//	} catch (NoSuchElementException e) {
//		Util.msg("end when expecting " + c);
//		return false;			
//	}
//	return true;
//}
//private Bexp getOneExpression(StringTokenizer vst) {
//	String t = vst.nextToken();
//	if (t.equals("and")) {
//		BexpAnd newNode = new BexpAnd();
//		try { // try not needed
//			if (!check(vst, '(')) return null;
//			newNode.setLeft(getOneExpression(vst));
//			if (!check(vst, ',')) return null;
//			newNode.setRight(getOneExpression(vst));
//			if (!check(vst, ')')) return null;
//			return newNode;
//		} catch (NoSuchElementException e) {
//			return null;
//		}
//	} else if (t.equals("or")) {
//		Util.msg("or not supported yet");
//		return null;
//	}
//	// should be comparison:
//	String sLeft = vst.nextToken();
//	String sOp = vst.nextToken();
//	if (sOp.length() != 1) return null;
//	switch (sOp.charAt(0)) {
//	case '<':
//		
//	}
//	
//	return null;
//}
//
///*
//* parseValidation()
//* Currently any validation string must be of the form ^. <opcode> att:<attributeID>$
//* Returns null for the slightest deviation from expected input.
//* MFK ideally would return bexp, i.e. a tree of conditions. Currently only supporting
//* single comparison, which must be BexpIntComparisonUnary.
//*/
//private BexpIntComparisonUnary parseValidation(String vs, Trial trl) {
//	String [] tokens = vs.split("\\s");
//	if (tokens.length != 3) {
//		Util.msg("Unexpected number of tokens (" + tokens.length + ") in trait validation (" + vs + ").");
//		return null;
//	}
//	
//	if (!tokens[0].equals(".")) {
//		Util.msg("Unexpected first token (" + tokens[0] + ") in trait validation.");
//		return null;
//	}
//
//	// Get the attribute we are comparing to:
//	String atty = tokens[2];
//	int alen = atty.length();
//	NodeProperty tup;
//	if (alen > 4) {
//		String saId = atty.substring(4);
//		long aid;
//		try {
//			aid = Integer.parseInt(saId);
//		} catch (NumberFormatException e) {
//			return null;
//		}
//		tup = trl.getAttribute(aid);
//	} else {
//		Util.msg("Invalid attribute token (" + tokens[2] + ") in trait validation.");
//		return null;
//	}
//	
//	BexpIntComparisonUnary.BexpIntComparator compOp;
//	String sCompOp = tokens[1];
//	if (sCompOp.equals("gt")) {
//		compOp = BexpIntComparisonUnary.BexpIntComparator.GT;
//	} else if (sCompOp.equals("ge")) {
//		compOp = BexpIntComparisonUnary.BexpIntComparator.GE;
//	} else if (sCompOp.equals("lt")) {
//		compOp = BexpIntComparisonUnary.BexpIntComparator.LT;
//	} else if (sCompOp.equals("le")) {
//		compOp = BexpIntComparisonUnary.BexpIntComparator.LE;
//	} else
//		return null;
//	
//	return new BexpIntComparisonUnary(tup, compOp);
////	StringTokenizer vst = new StringTokenizer(vs, "(),");
////	try {
////		return getOneExpression(vst);
////	} catch (NoSuchElementException e) {
////		Util.msg("missing element");
////		return null;
////	}
//
////	while (vst.hasMoreElements()) {
////		String t = vst.nextToken();
////		if (t.equals("and")) {
////			BexpAnd newNode = new BexpAnd();
////			try {
////				if (!vst.nextToken().equals("(")) {
////					Util.msg("missing (");
////					return null;
////				}
////				getOneExpression(vst)
////				
////				
////			} catch (NoSuchElementException e) {
////				
////			}
////			
////		}
////	}
////	return null;
//}
//
//
///*
//* initValidation()
//* Setup validation structures
//*/
//private void initValidation(Trial trl, String cond) {
//	if (cond != null && cond.length() > 0) {
//		mValidationBexp = parseValidation(cond, trl);
//		if (mValidationBexp != null)
//			mValidation = true;
//	}
//}
//



