
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;


public class Main {
    private final static int TIME_OUT = 10000;
    private final static int PORT = 53;
    private final static String dnsIPAddress = "8.8.8.8";
    private final static int BUF_SIZE = 8192;

    public static void main(String[] args) {
        List<String> ipList = new ArrayList<String>();
        Scanner scan = new Scanner(System.in);
        System.out.println("Please enter your address:");
        String domainName = scan.nextLine();
        System.out.println("Please enter record type(A default):");
        String type = scan.nextLine();

        try {
            query(dnsIPAddress, domainName, type, ipList);
        } catch (SocketTimeoutException ex) {
            System.out.println("Timeout");
        } catch (IOException ex) {
            System.out.println("Unexpected IOException: " + ex);
            ex.printStackTrace(System.out);
        }
        for (String ip : ipList) {
            System.out.println(ip);
        }
    }

    private static void query(String dnsServerIP, String domainName, String type, List<String> ipList)
            throws SocketTimeoutException, IOException {
        DatagramSocket socket = new DatagramSocket(0);
        socket.setSoTimeout(TIME_OUT);

        ByteArrayOutputStream outBuf = new ByteArrayOutputStream(BUF_SIZE);
        DataOutputStream output = new DataOutputStream(outBuf);

        encodeDNSMessage(output, domainName,type);

        InetAddress host = InetAddress.getByName(dnsServerIP);
        DatagramPacket request = new DatagramPacket(outBuf.toByteArray(), outBuf.size(), host, PORT);

        socket.send(request);

        byte[] inBuf = new byte[BUF_SIZE];
        ByteArrayInputStream inBufArray = new ByteArrayInputStream(inBuf);
        DataInputStream input = new DataInputStream(inBufArray);
        DatagramPacket response = new DatagramPacket(inBuf, inBuf.length);

        socket.receive(response);

        decodeDNSMessage(input, ipList);

        socket.close();
    }

    private static void encodeDNSMessage(DataOutputStream output, String domainName,String type)
            throws IOException {
        // transaction id
        output.writeShort(1);
        // flags
        output.writeShort(0x100);
        // number of queries
        output.writeShort(1);
        // answer, auth, other
        output.writeShort(0);
        output.writeShort(0);
        output.writeShort(0);

        encodeDomainName(output, domainName);

        // query type
        output.writeShort(getQueryType(type));
        // query class
        output.writeShort(1);

        output.flush();
    }

    private static void encodeDomainName(DataOutputStream output, String domainName)
            throws IOException {
        String[] splitted = domainName.split("[.]");
        for (int i = 0; i < splitted.length; i++) {
            String label = splitted[i];
            output.writeByte((byte) label.length());
            output.write(label.getBytes());
        }
        output.writeByte(0);
    }

    private static void decodeDNSMessage(DataInputStream input, List<String> ipList)
            throws IOException {
        // header
        // transaction id
        input.skip(2); //Skips over and discards n bytes of data from the input stream.
        // flags
        input.skip(2);
        // number of queries
        input.skip(2);
        // answer, auth, other
        short numberOfAnswer = input.readShort();
        input.skip(2);
        input.skip(2);

        // question record
        skipDomainName(input);
        // query type
        input.skip(2);
        // query class
        input.skip(2);

        // answer records
        for (int i = 0; i < numberOfAnswer; i++) {
            input.mark(1);
            byte ahead = input.readByte();
            input.reset();
            if ((ahead & 0xc0) == 0xc0) {
                // compressed name
                input.skip(2);
            } else {
                skipDomainName(input);
            }

            // query type
            short type = input.readShort();
            // query class
            input.skip(2);
            // ttl
            input.skip(4);
            short addrLen = input.readShort();
            if ((type == 1 || type == 2 || type == 5 || type == 6 || type == 15 || type == 39) && addrLen == 4) {
                int addr = input.readInt();
                ipList.add(longToIp(addr));
            } else {
                input.skip(addrLen);
            }
        }
    }

    private static void skipDomainName(DataInputStream input) throws IOException {
        byte labelLength = 0;
        do {
            labelLength = input.readByte();
            input.skip(labelLength);
        } while (labelLength != 0);
    }

    private static String longToIp(long ip) {
        return ((ip >> 24) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "."
                + ((ip >> 8) & 0xFF) + "." + (ip & 0xFF);
        /*The (ip >> 8), (ip >> 16) and (ip >> 24) moves the 2nd, 3rd and 4th bytes into the lower order byte,
         while the & 0xFF isolates the least significant byte at each step.*/
    }

    private static int getQueryType(String type) throws IOException {
        int id;
        switch (type.toUpperCase()) {
            case "NS":
                id = 2;   // NS record
                break;
            case "CNAME":
                id = 5;   // CNAME record
                break;
            case "MX":
                id = 15;  // MX record
                break;
            default:
                id = 1;   // A record
        }
        return id;
    }

}
