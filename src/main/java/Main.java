import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        String path = System.getenv("PATH");
        String[] dirs = path.split(":");
        Path currentDirectory = Paths.get(System.getProperty("user.dir"));
        while(true){
            System.out.print("$ ");
            String a = sc.nextLine();
            if (a.isEmpty()) continue;
            String[] parts = a.trim().split("\\s+");  
            String command = parts[0];
            if(command.equals("echo") || command.equals("exit")){
                if(command.equals("echo")){
                    for (int i = 1; i < parts.length; i++) {
                    System.out.print(parts[i]);
                    if (i < parts.length - 1) System.out.print(" ");
                    }
                    System.out.println();
                }   
                else if(command.equals("exit")){
                    break;
                }
            }
            else if(command.equals("type")){ 
                
                if(parts.length < 2){
                    System.out.println("type: missing argument");
                    continue;
                }

                String target = parts[1];
                
                if(target.equals("echo") || target.equals("exit") || target.equals("type") || target.equals("pwd") || target.equals("cd")){
                    System.out.println(target + " is a shell builtin");
                    continue;
                }


                boolean found = false;

                for(String dir : dirs){ 
                    Path p = Paths.get(dir, target); 
                    if(Files.exists(p) && Files.isExecutable(p)){ 
                        System.out.println(target + " is "+ p); 
                        found = true; break; 
                    } 
                } 
                
                if(found == false){ 
                    System.out.println(target + ": not found"); 
                }
            }
            else if(command.equals("pwd")){
                System.out.println(currentDirectory.toAbsolutePath());
            }
            else if(command.equals("cd")){
                if(parts.length < 2){
                    continue;
                }
                String target = parts[1];
                Path newpath = Paths.get(target);
                if(Files.isDirectory(newpath)){
                    currentDirectory = newpath.toAbsolutePath().normalize();
                }
                else{
                    System.out.println("cd: " + target + ": No such file or directory");
                }
            }
            else {
                String fullpath = null;

                for(String dir : dirs){ 
                    Path p = Paths.get(dir, command); 
                    if(Files.exists(p) && Files.isExecutable(p)){ 
                        fullpath = p.toString();
                        break;
                    } 
                } 

                if(fullpath == null){
                    System.out.println(command + ": command not found");
                    continue;
                }



                try {
                    List<String> cmd = new ArrayList<>();
                    for(int i = 0; i < parts.length; i++){
                        cmd.add(parts[i]);
                    }

                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    pb.inheritIO();
                    Process process = pb.start();
                    process.waitFor();

                } 
                catch (Exception e) {
                    System.out.println("Error executing command");
                }
            }
        }
    }
}
