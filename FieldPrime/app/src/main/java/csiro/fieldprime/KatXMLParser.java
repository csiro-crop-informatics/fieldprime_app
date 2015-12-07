/*
 * KatXMLParser.java
 * Michael Kirk 2013
 * 
 * Static method for creating a new dataset from a Katmandoo format xml file.
 */

package csiro.fieldprime;

import static csiro.fieldprime.Trait.Datatype.T_DECIMAL;
import static csiro.fieldprime.Trait.Datatype.T_INTEGER;
import static csiro.fieldprime.Trait.Datatype.T_STRING;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import android.util.Log;

public class KatXMLParser {
	// for some reason, Katmandoo seems to produce either of these variants.
	final static String ROWNUM_PREFIX = "RowNo";
	final static String COLNUM_PREFIX = "ColumnNo";
	final static String ROW_PREFIX = "Row";
	final static String COL_PREFIX = "Column";

	private static Document GetDomElement(String xml) {
		Document doc = null;
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder db = dbf.newDocumentBuilder();
			InputSource is = new InputSource();
			is.setCharacterStream(new StringReader(xml));
			// doc = db.parse(is);
			doc = db.parse(new ByteArrayInputStream(xml.getBytes()));

		} catch (ParserConfigurationException e) {
			Log.e("Error: ", e.getMessage());
			return null;
		} catch (SAXException e) {
			Log.e("Error: ", e.getMessage());
			return null;
		} catch (IOException e) {
			Log.e("Error: ", e.getMessage());
			return null;
		}
		// return DOM
		return doc;
	}

	/*
	 * getValue() XML worker functions to get the string value of a named item from an element. Returns null if named
	 * item not found.
	 */
	private static String getValue(Element item, String name) {
		NodeList n = item.getElementsByTagName(name);
		if (n.getLength() <= 0)
			return null;
		return getElementValue(n.item(0));
	}

	private static final String getElementValue(Node elem) {
		Node child;
		if (elem != null) {
			if (elem.hasChildNodes()) {
				for (child = elem.getFirstChild(); child != null; child = child.getNextSibling()) {
					if (child.getNodeType() == Node.TEXT_NODE) {
						return child.getNodeValue();
					}
				}
			}
		}
		return "";
	}

	/*
	 * ImportTrialKatmandooXMLFile()
	 * Create a new dataset based on the specified file, which should be an exported
	 * Katmandoo xml file. Returns NameStatusError returning the name of the created
	 * dataset, or an error message.
	 * 
	 * MFK Todo: Note on error we aren't cleaning up any created database elements.
	 */
	public static Result ImportTrialKatmandooXMLFile(String filename, Database db) {
		NodeList nl;
		Element el;

		String xml = Globals.ReadFileIntoString(filename, true);
		Document doc = GetDomElement(xml);
		if (doc == null) return new Result(false, "Cannot parse XML file");

		// Process Trial item (we are assuming there is only one of these):
		nl = doc.getElementsByTagName("Trial");
		if (nl.getLength() != 1)
			return new Result(false, "Unexpected number of Trial elements (" + nl.getLength() + ") Aborting");

		el = (Element) nl.item(0);
		String siteName = getValue(el, "SiteName");
		String siteYear = getValue(el, "SiteYear");
		String acronym = getValue(el, "TrialAcronym");
		Result res = Trial.CreateLocalTrial(filename, siteName, siteYear, siteName, acronym);
		if (res.bad()) {
			return res;
		}
		Trial ds = (Trial)res.obj();
		
		// Process Traits:
		nl = doc.getElementsByTagName("Trait");
		for (int i = 0; i < nl.getLength(); ++i) {
			el = (Element) nl.item(i);
			String tid = getValue(el, "TId");
			String caption = getValue(el, "TCaption");
			String unit = getValue(el, "TUnit");
			String rule = getValue(el, "TRule");

			// datatype (Katmandoo only support integer, decimal, and string):
			Trait.Datatype dt;
			double min = 0;
			double max = 100;
			String datatype = getValue(el, "TDataType");
			if (datatype.equals("Decimal")) {
				dt = T_DECIMAL;
				if (rule != null) {
					String[] minMax = rule.split("\\.\\.");
					if (minMax.length != 2) {
						return new Result(false, "Invalid validation rule");
					}
					min = Double.parseDouble(minMax[0]);
					max = Double.parseDouble(minMax[1]);
				}
			} else if (datatype.equals("Integer")) {
				dt = T_INTEGER;
			} else if (datatype.equals("String")) {
				dt = T_STRING;
			} else { // Unexpected:
				return new Result(false, "Unexpected dataype in trait (" + datatype + ") Aborting");
			}

			if (datatype.equals("Decimal")) {
			}
			ds.AddAdHocTrait(caption, "", dt, tid, unit, min, max);
		}

		// Process Genotypes. We need these as the Trial units refer to them (by the GId) field.
		// Hence we store them in a hashmap.
		nl = doc.getElementsByTagName("Genotype");
		class genostruct {
			String gname;
			String ped;

			genostruct(String gname, String ped) {
				this.gname = gname;
				this.ped = ped;
			}
		}
		HashMap genos = new HashMap();
		for (int i = 0; i < nl.getLength(); ++i) {
			el = (Element) nl.item(i);
			String gid = getValue(el, "GId");
			String gname = getValue(el, "GName");
			String ped = getValue(el, "Ped");
			genostruct gs = new genostruct(gname, ped);
			genos.put(gid, gs);
		}

		// Process Trial Units:
		nl = doc.getElementsByTagName("TrialUnit");
		int maxRow = -1, maxCol = -1;
		for (int i = 0; i < nl.getLength(); ++i) {
			el = (Element) nl.item(i);

			String tuid = getValue(el, "TUId");
			String pos = getValue(el, "Pos");
			String gid = getValue(el, "GId");
			String barcode = getValue(el, "Barcode");

			// Parse the Pos field. We are assuming this is a list separated by the pipe character
			// and that there will be fields ColumnNo<col num> and RowNo<row num>
			String[] posFields = pos.split("\\|");
			int row = -1, col = -1;
			for (int j = 0; j < posFields.length; j++) {
				String str = posFields[j];
				if (str.startsWith(COLNUM_PREFIX)) {
					String scol = str.substring(COLNUM_PREFIX.length());
					col = Integer.valueOf(scol);
				} else if (str.startsWith(ROWNUM_PREFIX)) {
					String srow = str.substring(ROWNUM_PREFIX.length());
					row = Integer.valueOf(srow);
				} else if (str.startsWith(COL_PREFIX)) {
					String scol = str.substring(COL_PREFIX.length());
					col = Integer.valueOf(scol);
				} else if (str.startsWith(ROW_PREFIX)) {
					String srow = str.substring(ROW_PREFIX.length());
					row = Integer.valueOf(srow);
				}
			}
			if (row == -1 || col == -1) {
				return new Result(false, "Invalid row or column in TrialUnit - Aborting");
			}

			// Get the Genotype name and pedigree from the genotype hashmap:
			genostruct gs = (genostruct) genos.get(gid);
			if (gs == null) {
				return new Result(false, "Missing genotype data (" + gid + ") for TrialUnit - Aborting");
			}

			res = ds.addNode(row, col, pos, barcode);
			if (!res.good())
				return new Result(false, res.errMsg());
		}

		return new Result(true, ds.getName());
	}
}
