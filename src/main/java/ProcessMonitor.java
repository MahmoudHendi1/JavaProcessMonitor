import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

import javax.management.*;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import java.io.InputStreamReader;
import java.text.CharacterIterator;
import java.text.SimpleDateFormat;
import java.text.StringCharacterIterator;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class ProcessMonitor extends TimerTask {
    Timer t;
    MBeanServerConnection mbsc;
    boolean header=false;
    CSVPrinter csvPrinter;
    SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    String pid;
    public static String humanReadableByteCountBin(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return bytes + " B";
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format("%.1f %ciB", value / 1024.0, ci.current());
    }
    ProcessMonitor(JMXConnector _connector, Timer _t , BufferedWriter writer,String _pid) throws IOException {
        mbsc = _connector.getMBeanServerConnection();
        t=_t;
        csvPrinter = new CSVPrinter(writer, CSVFormat.EXCEL.withHeader("Date"
                ,"ProcMem"
                ,"TotalMemory"
                ,"UsedHeap"
                ,"Non-Heap"
                ,"CpuLoad"
                ,"ThreadCount"
                ,"OpenFileDescriptorCount"
                ,"MaxFileDescriptorCount"
                ,"AvailableProcessors"
        ));
        pid=_pid;
    }
    long getProcessMemory() throws IOException, InterruptedException {
        String[] cmd = {"/bin/sh"
                ,"-c"
                ,"cat /proc/"+ pid + "/smaps | grep -i pss  | awk '{Total+=$2} END {print Total}' "};
        Runtime run = Runtime.getRuntime();
        Process pr = run.exec(cmd);
        pr.waitFor();
        BufferedReader buf = new BufferedReader(new InputStreamReader(pr.getInputStream()));
        String line;
        line=buf.readLine();
        return Long.parseLong(line)*1024;
    }
    public void run() {
        try {
            if(!header){
                System.out.println("ProcMem"
                        + "\t\tTotMemory"
                        + "    \t\tUsedHeap"
                        + "\t\tNonHeap"
                        + "  \t\t\tCpuLoad"
                        + "\t\tThreadCnt"
                        + "\t\tOpenFlDescCnt"
                        + "\t\tMxFlDescCnt"
                        + "\t\tAvaProcessors");
                header=true;
            }
            Date date = new Date();


            CompositeData cdHeapMemory = (CompositeData) mbsc.getAttribute(new ObjectName("java.lang:type=Memory"), "HeapMemoryUsage");
            CompositeData cdNonHeapMemory = (CompositeData) mbsc.getAttribute(new ObjectName("java.lang:type=Memory"), "NonHeapMemoryUsage");
            long usedHeap=Long.parseLong(cdHeapMemory.get("used").toString());
            long nonHeap=Long.parseLong(cdNonHeapMemory.get("used").toString());
            long totalMemory=usedHeap+nonHeap;
            Double processCpuLoad = (Double) mbsc.getAttribute(new ObjectName("java.lang:type=OperatingSystem"), "ProcessCpuLoad");
            long OpenFileDescriptorCount = (long) mbsc.getAttribute(new ObjectName("java.lang:type=OperatingSystem"),"OpenFileDescriptorCount");
            long MaxFileDescriptorCount = (long) mbsc.getAttribute(new ObjectName("java.lang:type=OperatingSystem"),"MaxFileDescriptorCount");
            int AvailableProcessors = (int) mbsc.getAttribute(new ObjectName("java.lang:type=OperatingSystem"),"AvailableProcessors");
            int threadCount= (int) mbsc.getAttribute(new ObjectName("java.lang:type=Threading"), "ThreadCount");
            long procMem= getProcessMemory();
            csvPrinter.printRecord(formatter.format(date)
                    ,procMem
                    ,totalMemory
                    ,usedHeap
                    ,nonHeap
                    ,processCpuLoad
                    ,threadCount
                    ,OpenFileDescriptorCount
                    ,MaxFileDescriptorCount
                    ,AvailableProcessors);


            System.out.println(humanReadableByteCountBin(procMem)
                    + "\t\t" + humanReadableByteCountBin(totalMemory)
                    + "     \t\t" + humanReadableByteCountBin(usedHeap)
                    + "    \t" + humanReadableByteCountBin(nonHeap)
                    + "    \t\t" + String.format("%,3.3f", processCpuLoad)
                    + "\t\t\t" +threadCount
                    + "\t\t\t" + OpenFileDescriptorCount
                    + "\t\t\t\t\t" +MaxFileDescriptorCount
                    + "\t\t\t" +AvailableProcessors);
            csvPrinter.flush();


        } catch (MBeanException | AttributeNotFoundException | InstanceNotFoundException | ReflectionException | IOException | MalformedObjectNameException | InterruptedException e) {
            System.out.println("The process is terminated");
            e.printStackTrace();
            header=false;
            t.cancel();
        }

    }

}
