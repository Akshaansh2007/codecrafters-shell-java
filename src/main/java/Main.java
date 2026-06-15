import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        String path = System.getenv("PATH");
        String[] dirs = path.split(":");
        while(true){
            System.out.print("$ ");
            String a = sc.nextLine();
            if(a.equals("exit")){break;}
            if(a.startsWith("echo")){
                System.out.println(a.substring(5));
                continue;
            }
            if(a.startsWith("type")){
                String command = a.substring(5);
                if(command.equals("exit") || command.equals("echo") || command.equals("type")){
                    System.out.println(command + " is a shell builtin");
                    continue;
                }
                boolean found = false;

                for(String dir : dirs){
                    Path p = Paths.get(dir, command);
                    if(Files.exists(p) && Files.isExecutable(p)){
                        System.out.println(command + " is "+ p);
                        found = true;
                        break;
                    }
                }

                if(found == false){
                    System.out.println(command +  ": not found");
                }
                continue;
            }
            System.out.println(a +  ": command not found");
        }
    }
}
