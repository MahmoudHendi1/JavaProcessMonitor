import com.sun.tools.attach.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Timer;
import com.sun.tools.attach.VirtualMachine;

public class Main {

    public static void main(String[] args) throws AttachNotSupportedException, IOException{
        System.out.println("BEGIN");


        if(args.length<1){
            System.out.println("Fail, not enough arguments");
            return;
        }
        List<VirtualMachineDescriptor> vmList = VirtualMachine.list();
        boolean found=false;
        String vmName="";
        for(VirtualMachineDescriptor vm: vmList){
            if(vm.id().equals(args[0])){
                found=true;
                vmName=vm.displayName();
                break;
            }
        }
        if (!found){
            System.out.println("Process can't be found");
            return;
        }
        VirtualMachine virtualMachine = VirtualMachine.attach(args[0]);
        String connectorAddr = virtualMachine.startLocalManagementAgent();

        if (connectorAddr == null) {
            connectorAddr = virtualMachine.getAgentProperties().getProperty(
                    "com.sun.management.jmxremote.localConnectorAddress");
        }

        if(args.length>1){
            vmName=args[1];
        }

        JMXServiceURL serviceURL = new JMXServiceURL(connectorAddr);
        try {

            JMXConnector connector = JMXConnectorFactory.connect(serviceURL);

            BufferedWriter writer = Files.newBufferedWriter(Paths.get(vmName+".csv"));


            Timer t=new Timer();
            ProcessMonitor m= new ProcessMonitor(connector,t,writer,args[0]);
            virtualMachine.detach();
            t.scheduleAtFixedRate(m, 0,1000);
        }catch (IOException ignored){
            System.out.println("The connector client or the connection cannot be made because of a communication problem.");
        }catch (SecurityException e){
            System.out.println("The connection cannot be made for security reasons.");
        }


    }


}
