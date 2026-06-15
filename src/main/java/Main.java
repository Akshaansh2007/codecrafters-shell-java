import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        // TODO: Uncomment the code below to pass the first stage
        while(true){
            System.out.print("$ ");
            String a = sc.nextLine();
            System.out.println(a +  ": command not found");
        }
    }
}
