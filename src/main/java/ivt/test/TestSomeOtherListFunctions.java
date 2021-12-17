package ivt.test;

import java.util.ArrayList;
import java.util.List;

public class TestSomeOtherListFunctions {

	public static void main(String[] args) {
		// TODO Auto-generated method stub

		final List<String> listToBeModified = new ArrayList<String>();
		final String origin = "starting here";
		final List<String> listToBeInserted = new ArrayList<String>();
		final String destination = "ending here";
		
		listToBeModified.add("first thing");
		listToBeModified.add("second thing");
		listToBeModified.add("starting here");
		listToBeModified.add("original first stage");
		listToBeModified.add("original second stage");
		listToBeModified.add("original third stage");
		listToBeModified.add("ending here");
		listToBeModified.add("third thing");
		listToBeModified.add("fourth thing");
		
		listToBeInserted.add("starting here");
		listToBeInserted.add("replacement first stage");
		listToBeInserted.add("Replacement second stage");
		listToBeInserted.add("Replacement third stage");
		listToBeInserted.add("ending here");
		
		System.out.println("This is the list to be inserted now: " + listToBeInserted);
		System.out.println("This is the list to be modified now: " + listToBeModified);
		
		List<String> dropUntilList = dropUntil(listToBeModified, "original first stage");
		
		System.out.println("this is the dropUntil list now: " + dropUntilList);
		System.out.println("This is the list to be inserted now: " + listToBeInserted);
		System.out.println("This is the list to be modified now: " + listToBeModified);

	}
	
	private static <E> List<E> dropUntil(List<E> lst, E cmp) {
		// Note: Java versions >= 9 contain something like this in the Streams API
		List<E> result = new ArrayList<>();
		
		for (E el : lst) {
			if (!result.isEmpty() || el.equals(cmp)) {
				result.add(el);
			}
		}
		return result;
		
	}

}
