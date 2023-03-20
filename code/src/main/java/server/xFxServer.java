package server;

import java.net.*;
import java.io.*;
import java.util.*;

public class xFxServer {

	public static byte[] convertMaptoBytes(Map<String, Long> filesData) {
		// Converting the Map to an array of bytes

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		
		try {
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(filesData);
			return baos.toByteArray(); 
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}

	}


	public static void main(String[] args) throws Exception {

		try (ServerSocket ss = new ServerSocket(80)) {
			while (true) {
				System.out.println("Server waiting...");
				Socket connectionFromClient = ss.accept();
				System.out.println(
						"Server got a connection from a client whose port is: " + connectionFromClient.getPort());

				try {
					// I/O operations
					InputStream in = connectionFromClient.getInputStream();
					OutputStream out = connectionFromClient.getOutputStream();

					BufferedReader headerReader = new BufferedReader(new InputStreamReader(in));
					BufferedWriter headerWriter = new BufferedWriter(new OutputStreamWriter(out));

					DataInputStream dataIn = new DataInputStream(in);
					DataOutputStream dataOut = new DataOutputStream(out);

					String header = headerReader.readLine();
					StringTokenizer strk = new StringTokenizer(header, " ");

					String command = strk.nextToken();

					// Preparing Error Messages
					String errorMessage = "ERR\n";
					String notConnected = "Connection from an incompatible client\n";
					String completeFile = "COMPLETE\n";
					String uploadError = "UPERR\n";
					String successUpload = "SUCCESS\n";

					if (command.equals("download")) {
						try {
							String fileName = strk.nextToken();
							FileInputStream fileIn = new FileInputStream("ServerShare/" + fileName);
							int fileSize = fileIn.available();
							header = "OK " + fileSize + "\n";

							headerWriter.write(header, 0, header.length());
							headerWriter.flush();

							byte[] bytes = new byte[fileSize];
							fileIn.read(bytes);

							fileIn.close();
							// sending the corresponding bytes
							dataOut.write(bytes, 0, fileSize);
							dataOut.flush();

						} catch (Exception ex) {
							headerWriter.write(errorMessage, 0, errorMessage.length());
							headerWriter.flush();
						} finally {
							connectionFromClient.close();
						}
					} else if (command.equals("check")) {
						String fileName = strk.nextToken();
						Long fileLastModifiedClient = Long.parseLong(strk.nextToken());

						File fileServer = new File("ServerShare/" + fileName);

						Long fileLastModifiedServer = fileServer.lastModified();
						int fileSize = (int) fileServer.length();
						// assuming that the file gets modified only in server-side
						if (fileLastModifiedClient > fileLastModifiedServer) {
							header = "OK\n";
							headerWriter.write(header, 0, header.length());
							headerWriter.flush();
							connectionFromClient.close();
						} else {
							header = "DIRTY " + fileSize + "\n";
							headerWriter.write(header, 0, header.length());
							headerWriter.flush();

							byte[] bytes = new byte[fileSize];
							FileInputStream fileIn = new FileInputStream(fileServer);
							fileIn.read(bytes);

							fileIn.close();
							// sending the corresponding bytes
							dataOut.write(bytes, 0, fileSize);
							dataOut.flush();

							connectionFromClient.close();
						}

					} else if (command.equals("upload")) {
						try {
							String fileName = strk.nextToken();
							FileOutputStream fileOut = new FileOutputStream("ServerShare/" + fileName);
							int fileSize = Integer.parseInt(strk.nextToken());

							byte[] bytes = new byte[fileSize];
							dataIn.readFully(bytes);

							fileOut.write(bytes, 0, fileSize);

							fileOut.close();
							// sending success header
							headerWriter.write(successUpload, 0, successUpload.length());
							headerWriter.flush();

						} catch (Exception e) {
							//signal error
							headerWriter.write(uploadError, 0, uploadError.length());
							headerWriter.flush();
						} finally {
							connectionFromClient.close();
						}
					} else if (command.equals("list")) {
						try {
							//initializing total size
							Long totalBytes = 0L;
							File folder = new File("ServerShare");
							File[] listOfFiles = folder.listFiles();

							
							if (listOfFiles.length == 0 || listOfFiles == null) {
								// signal error
								errorMessage = "LISTERR\n";
								headerWriter.write(errorMessage, 0, errorMessage.length());
								headerWriter.flush();
								continue;
							}

							Map<String, Long> filesInfoServer = new TreeMap<>();

							for (int i = 0; i < listOfFiles.length; i++) {
								if (listOfFiles[i].isFile()) {
									totalBytes += listOfFiles[i].length();
									filesInfoServer.put(listOfFiles[i].getName(), listOfFiles[i].length());
								}
							}
						
							header = "OK " + totalBytes.intValue() + " \n";
							headerWriter.write(header);
							headerWriter.flush();
							// preparing the array of bytes to be sent
							byte[] bytes = new byte[totalBytes.intValue()];
							bytes = convertMaptoBytes(filesInfoServer);
							dataOut.write(bytes);
							dataOut.flush();
						} catch (Exception e) {
							headerWriter.write(errorMessage, 0, errorMessage.length());
							headerWriter.flush();
						} finally {
							connectionFromClient.close();
						}
					} else if (command.equals("resume")) {

						try {
							String fileName = strk.nextToken();
							int bytesSent = Integer.parseInt(strk.nextToken());
							FileInputStream fileIn = new FileInputStream("ServerShare/" + fileName);
							int totalBytes = fileIn.available();
							int bytesLeft = totalBytes - bytesSent;

							if (bytesLeft == 0) {
								headerWriter.write(completeFile, 0, completeFile.length());
								headerWriter.flush();
								continue;
							}

							header = "OK " + bytesLeft + " \n";
							headerWriter.write(header);
							headerWriter.flush();
							// preparing the array of bytes left to complete the download
							byte[] bytes = new byte[bytesLeft];
							//skipping the bytes already sent
							fileIn.skip(bytesSent);
							fileIn.read(bytes);

							fileIn.close();

							dataOut.write(bytes, 0, bytesLeft);
							dataOut.flush();
						} catch (Exception e) {
							headerWriter.write(errorMessage, 0, errorMessage.length());
							headerWriter.flush();

						} finally {
							connectionFromClient.close();
						}

					} else {

						System.out.println(notConnected);

					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
}
