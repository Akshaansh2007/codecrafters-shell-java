import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        // TODO: Uncomment the code below to pass the first stage
        while(true){
            System.out.print("$ ");
            String a = sc.nextLine();
            if(a.equals("exit")){break;}
            if(a.startsWith("echo")){
                System.out.println(a.substring(5));
                continue;
            }
            if(a.startsWith("type")){
                if(a.substring(5).equals("exit") || a.substring(5).equals("echo") || a.substring(5).equals("type")){
                    System.out.println(a.substring(5) + " is a shell builtin");
                }
                else{
                    System.out.println(a.substring( 5) +  ": not found");
                }
                continue;
            }
            System.out.println(a +  ": command not found");
        }
    }
}
