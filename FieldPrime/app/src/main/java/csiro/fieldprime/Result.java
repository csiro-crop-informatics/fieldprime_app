package csiro.fieldprime;

public class Result {
	private String stringField;   // could use objField for this
	private boolean isGood = false;
	private String errMsg;
	private Object objField;

	// Create good or bad result, with stringField or errMsg respectively:
	Result(boolean isGood, String nameOrError) {
		this.isGood = isGood;
		if (isGood)
			stringField = nameOrError;
		else
			errMsg = nameOrError;
	}
	
	// Create good result with included object:
	Result(Object thingy) {
		this.isGood = true;
		objField = thingy;
	}
	
	// Create good result with nothing:
	Result() {
		this.isGood = true;
	}
	
	public boolean good() {
		return isGood;
	}
	
	public boolean bad() {
		return !isGood;
	}
	
	public Object obj() {
		return objField;
	}
	
	public String errMsg() {
		return errMsg;
	}
	
	public String string() {
		return stringField;
	}
}
