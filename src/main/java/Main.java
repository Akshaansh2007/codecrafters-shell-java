import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
public class Main {
    static class Job {
        int id;
        long pid;
        String command;
        String status;
        Process process;
        Job(int id, long pid, String command, String status, Process process) {
            this.id = id;
            this.pid = pid;
            this.command = command;
            this.status = status;
            this.process = process;
        }
    }
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        List<Job> jobList = new ArrayList<>();
        while (true) {
            System.out.print("$ ");
            System.out.flush();
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                checkFinishedJobs(jobList);
                continue;
            }
            if (input.equals("exit")) {
                break;
            }
            List<String> tokens = parseInput(input);
            if (tokens.isEmpty()) {
                checkFinishedJobs(jobList);
                continue;
            }
            boolean runInBackground = false;
            if (tokens.get(tokens.size() - 1).equals("&")) {
                runInBackground = true;
                tokens.remove(tokens.size() - 1);
            }
            if (tokens.isEmpty()) {
                checkFinishedJobs(jobList);
                continue;
            }
            String command = tokens.get(0);
            boolean hasPipe = false;
            for (String token : tokens) {
                if (token.equals("|")) {
                    hasPipe = true;
                    break;
                }
            }
            if (hasPipe) {
                runPipeline(tokens, input, runInBackground, jobList);
                if (!command.equals("jobs")) {
                    checkFinishedJobs(jobList);
                }
                continue;
            }
            String outputFile = null;
            String errorFile = null;
            boolean appendOutput = false;
            boolean appendError = false;
            int redirectAt = -1;
            for (int i = tokens.size() - 2; i >= 0; i--) {
                String token = tokens.get(i);
                if (token.equals(">") || token.equals("1>")) {
                    redirectAt = i;
                    outputFile = tokens.get(i + 1);
                    appendOutput = false;
                    break;
                } else if (token.equals(">>") || token.equals("1>>")) {
                    redirectAt = i;
                    outputFile = tokens.get(i + 1);
                    appendOutput = true;
                    break;
                } else if (token.equals("2>")) {
                    redirectAt = i;
                    errorFile = tokens.get(i + 1);
                    appendError = false;
                    break;
                } else if (token.equals("2>>")) {
                    redirectAt = i;
                    errorFile = tokens.get(i + 1);
                    appendError = true;
                    break;
                }
            }
            if (redirectAt != -1) {
                tokens.remove(redirectAt + 1);
                tokens.remove(redirectAt);
            }
            if (tokens.isEmpty()) {
                if (!command.equals("jobs")) checkFinishedJobs(jobList);
                continue;
            }
            java.io.PrintStream savedOut = System.out;
            java.io.PrintStream savedErr = System.err;
            java.io.PrintStream fileOut = null;
            java.io.PrintStream fileErr = null;
            if (outputFile != null) {
                try {
                    File f = new File(outputFile);
                    if (f.getParentFile() != null) f.getParentFile().mkdirs();
                    fileOut = new java.io.PrintStream(new FileOutputStream(f, appendOutput));
                    System.setOut(fileOut);
                } catch (Exception e) {
                    System.err.println("Shell: Cannot write to " + outputFile);
                    if (!command.equals("jobs")) checkFinishedJobs(jobList);
                    continue;
                }
            }
            if (errorFile != null) {
                try {
                    File f = new File(errorFile);
                    if (f.getParentFile() != null) f.getParentFile().mkdirs();
                    fileErr = new java.io.PrintStream(new FileOutputStream(f, appendError));
                    System.setErr(fileErr);
                } catch (Exception e) {
                    System.err.println("Shell: Cannot write to " + errorFile);
                    if (fileOut != null) fileOut.close();
                    System.setOut(savedOut);
                    if (!command.equals("jobs")) checkFinishedJobs(jobList);
                    continue;
                }
            }
            try {
                if (isBuiltin(command)) {
                    runBuiltin(command, tokens, jobList);
                    continue;
                }
                String executablePath = findInPath(command);
                if (executablePath == null) {
                    System.err.println(command + ": command not found");
                    continue;
                }
                ProcessBuilder pb = new ProcessBuilder(tokens);
                pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                if (outputFile != null) {
                    pb.redirectOutput(appendOutput
                        ? ProcessBuilder.Redirect.appendTo(new File(outputFile))
                        : ProcessBuilder.Redirect.to(new File(outputFile)));
                } else {
                    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                }
                if (errorFile != null) {
                    pb.redirectError(appendError
                        ? ProcessBuilder.Redirect.appendTo(new File(errorFile))
                        : ProcessBuilder.Redirect.to(new File(errorFile)));
                } else {
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                }
                Process process = pb.start();
                if (runInBackground) {
                    int jobId = getNextJobId(jobList);
                    long pid = process.pid();
                    savedOut.println("[" + jobId + "] " + pid);
                    insertJob(jobList, new Job(jobId, pid, input, "Running", process));
                } else {
                    process.waitFor();
                }
            } catch (Exception e) {
                System.err.println("Error running command: " + command);
            } finally {
                if (fileOut != null) { fileOut.close(); System.setOut(savedOut); }
                if (fileErr != null) { fileErr.close(); System.setErr(savedErr); }
                System.out.flush();
                System.err.flush();
                if (!command.equals("jobs")) checkFinishedJobs(jobList);
            }
        }
    }
    private static void runPipeline(List<String> tokens, String rawInput,
                                     boolean runInBackground, List<Job> jobList) {
        try {
            List<List<String>> stages = new ArrayList<>();
            List<String> current = new ArrayList<>();
            for (String token : tokens) {
                if (token.equals("|")) {
                    stages.add(current);
                    current = new ArrayList<>();
                } else {
                    current.add(token);
                }
            }
            stages.add(current);
            java.io.InputStream currentInput = System.in;
            java.io.PrintStream savedOut = System.out;
            java.io.InputStream savedIn = System.in;
            List<Thread> threads = new ArrayList<>();
            Process lastProcess = null;
            for (int i = 0; i < stages.size(); i++) {
                List<String> stage = stages.get(i);
                boolean isLast = (i == stages.size() - 1);
                java.io.PipedOutputStream pipeOut = null;
                java.io.PipedInputStream pipeIn = null;
                if (!isLast) {
                    pipeOut = new java.io.PipedOutputStream();
                    pipeIn = new java.io.PipedInputStream(pipeOut);
                }
                final java.io.InputStream stageIn = currentInput;
                final java.io.PipedOutputStream stageOut = pipeOut;
                if (isBuiltin(stage.get(0))) {
                    Thread t = new Thread(() -> {
                        try {
                            if (stageIn != System.in) System.setIn(stageIn);
                            if (!isLast) {
                                System.setOut(new java.io.PrintStream(stageOut, true));
                            } else {
                                System.setOut(savedOut);
                            }
                            runBuiltin(stage.get(0), stage, jobList);
                        } catch (Exception e) {
                        } finally {
                            try { if (stageOut != null) stageOut.close(); } catch (Exception ignored) {}
                            try { if (stageIn != System.in) stageIn.close(); } catch (Exception ignored) {}
                        }
                    });
                    threads.add(t);
                    t.start();
                } else {
                    ProcessBuilder pb = new ProcessBuilder(stage);
                    if (stageIn == System.in) pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                    if (isLast) pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    Process p = pb.start();
                    if (isLast) lastProcess = p;
                    if (stageIn != System.in) {
                        Thread feeder = new Thread(() -> {
                            try (java.io.OutputStream out = p.getOutputStream();
                                 java.io.InputStream in = stageIn) {
                                byte[] buf = new byte[4096];
                                int n;
                                while ((n = in.read(buf)) != -1) {
                                    out.write(buf, 0, n);
                                    out.flush();
                                }
                            } catch (Exception ignored) {}
                        });
                        threads.add(feeder);
                        feeder.start();
                    }
                    if (!isLast) {
                        Thread extractor = new Thread(() -> {
                            try (java.io.InputStream in = p.getInputStream();
                                 java.io.PipedOutputStream out = stageOut) {
                                byte[] buf = new byte[4096];
                                int n;
                                while ((n = in.read(buf)) != -1) {
                                    out.write(buf, 0, n);
                                    out.flush();
                                }
                            } catch (Exception ignored) {}
                        });
                        threads.add(extractor);
                        extractor.start();
                    }
                }
                currentInput = pipeIn;
            }
            System.setIn(savedIn);
            System.setOut(savedOut);
            if (runInBackground && lastProcess != null) {
                int jobId = getNextJobId(jobList);
                System.out.println("[" + jobId + "] " + lastProcess.pid());
                insertJob(jobList, new Job(jobId, lastProcess.pid(), rawInput, "Running", lastProcess));
            } else {
                if (lastProcess != null) lastProcess.waitFor();
                for (Thread t : threads) t.join();
            }
        } catch (Exception e) {
            System.err.println("Error running pipeline");
        }
    }
    private static boolean isBuiltin(String cmd) {
        return cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type")
            || cmd.equals("pwd") || cmd.equals("cd") || cmd.equals("jobs");
    }
    private static void runBuiltin(String cmd, List<String> args, List<Job> jobList) {
        switch (cmd) {
            case "echo": {
                StringBuilder output = new StringBuilder();
                for (int i = 1; i < args.size(); i++) {
                    if (i > 1) output.append(" ");
                    output.append(args.get(i));
                }
                System.out.println(output);
                break;
            }
            case "pwd": {
                System.out.println(System.getProperty("user.dir"));
                break;
            }
            case "cd": {
                String path = args.size() > 1 ? args.get(1) : "~";
                File target;
                if (path.equals("~")) {
                    target = new File(System.getenv("HOME"));
                } else if (path.startsWith("/")) {
                    target = new File(path);
                } else {
                    target = new File(System.getProperty("user.dir"), path);
                }
                try {
                    File resolved = new File(target.getCanonicalPath());
                    if (resolved.isDirectory()) {
                        System.setProperty("user.dir", resolved.getAbsolutePath());
                    } else {
                        System.err.println("cd: " + path + ": No such file or directory");
                    }
                } catch (Exception e) {
                    System.err.println("cd: " + path + ": No such file or directory");
                }
                break;
            }
            case "type": {
                String target = args.size() > 1 ? args.get(1) : "";
                if (isBuiltin(target)) {
                    System.out.println(target + " is a shell builtin");
                } else {
                    String found = findInPath(target);
                    if (found != null) {
                        System.out.println(target + " is " + found);
                    } else {
                        System.err.println(target + ": not found");
                    }
                }
                break;
            }
            case "jobs": {
                int size = jobList.size();
                List<Job> done = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    Job job = jobList.get(i);
                    if (!job.process.isAlive()) job.status = "Done";
                    char marker = ' ';
                    if (i == size - 1) marker = '+';
                    else if (i == size - 2) marker = '-';
                    String displayCmd = job.command;
                    if (job.status.equals("Done") && displayCmd.endsWith(" &")) {
                        displayCmd = displayCmd.substring(0, displayCmd.length() - 2);
                    }
                    System.out.printf("[%d]%c  %-24s%s\n", job.id, marker, job.status, displayCmd);
                    if (job.status.equals("Done")) done.add(job);
                }
                jobList.removeAll(done);
                break;
            }
        }
    }
    private static void checkFinishedJobs(List<Job> jobList) {
        int size = jobList.size();
        List<Job> done = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Job job = jobList.get(i);
            if (!job.process.isAlive() && job.status.equals("Running")) {
                job.status = "Done";
                char marker = ' ';
                if (i == size - 1) marker = '+';
                else if (i == size - 2) marker = '-';
                String displayCmd = job.command;
                if (displayCmd.endsWith(" &")) {
                    displayCmd = displayCmd.substring(0, displayCmd.length() - 2);
                }
                System.out.printf("[%d]%c  %-24s%s\n", job.id, marker, job.status, displayCmd);
                done.add(job);
            }
        }
        jobList.removeAll(done);
    }
    private static String findInPath(String cmd) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        for (String dir : pathEnv.split(File.pathSeparator)) {
            File f = new File(dir, cmd);
            if (f.exists() && f.canExecute()) {
                return f.getAbsolutePath();
            }
        }
        return null;
    }
    private static int getNextJobId(List<Job> jobList) {
        int id = 1;
        while (true) {
            boolean taken = false;
            for (Job job : jobList) {
                if (job.id == id) { taken = true; break; }
            }
            if (!taken) return id;
            id++;
        }
    }
    private static void insertJob(List<Job> jobList, Job newJob) {
        int index = 0;
        while (index < jobList.size() && jobList.get(index).id < newJob.id) {
            index++;
        }
        jobList.add(index, newJob);
    }
    private static List<String> parseInput(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean hasContent = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' && !inSingleQuotes && !inDoubleQuotes) {
                if (i + 1 < input.length()) {
                    current.append(input.charAt(++i));
                    hasContent = true;
                } else {
                    current.append(c);
                    hasContent = true;
                }
            } else if (c == '\\' && inDoubleQuotes) {
                if (i + 1 < input.length()) {
                    char next = input.charAt(i + 1);
                    if (next == '"' || next == '\\' || next == '$' || next == '`') {
                        current.append(next);
                        i++;
                    } else {
                        current.append(c);
                    }
                    hasContent = true;
                } else {
                    current.append(c);
                    hasContent = true;
                }
            } else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                hasContent = true;
            } else if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                hasContent = true;
            } else if (inSingleQuotes || inDoubleQuotes) {
                current.append(c);
            } else if (Character.isWhitespace(c)) {
                if (current.length() > 0 || hasContent) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    hasContent = false;
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0 || hasContent) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}