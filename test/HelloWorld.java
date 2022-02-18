/**
 * Debug this program with JDI & read all local variables.
 * 
 * @author ravik
 *
 */
public class HelloWorld {
 
	public static void main(String[] args) {
		String helloWorld = "Hello World. ";
 
		String welcome = "Welcome to Its All Binary !";
 
		for (int i = 0; i < 10; i ++) {
			String greeting = helloWorld + welcome + i;

			System.out.println("Hi Everyone, " + greeting);// Put a break point at this line.	
		}
 
	}
 
}