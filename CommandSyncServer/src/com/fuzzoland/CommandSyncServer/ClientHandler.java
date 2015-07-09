package com.fuzzoland.CommandSyncServer;

import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClientHandler implements Runnable {

	private CSS plugin;
	private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Integer heartbeat = 0;
    private String name;
    private String pass;
    private String version = "2.3";
    private Boolean active = false;

	public ClientHandler(CSS plugin, Socket socket, Integer heartbeat, String pass) throws IOException {
		this.plugin = plugin;
		this.socket = socket;
		this.heartbeat = heartbeat;
		this.pass = pass;
        this.active = true;
        out = new PrintWriter(socket.getOutputStream(), true);
		in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		plugin.debugger.debug("Received new connection from " + socket.getInetAddress().getHostName() + ":" + socket.getPort() + ".");
		name = in.readLine();
		if(plugin.c.contains(name)) {
		    plugin.debugger.debug("[" + socket.getInetAddress().getHostName() + ":" + socket.getPort() + "] [" + name + "] Provided a name that is already connected.");
		    out.println("n");
		    socket.close();
		    return;
		}
		out.println("y");
		if(!in.readLine().equals(this.pass)) {
		    plugin.debugger.debug("[" + socket.getInetAddress().getHostName() + ":" + socket.getPort() + "] [" + name + "] Provided an invalid password.");
			out.println("n");
			socket.close();
			return;
		}
		out.println("y");
		String version = in.readLine();
		if(!version.equals(this.version)) {
		    plugin.debugger.debug("[" + socket.getInetAddress().getHostName() + ":" + socket.getPort() + "] [" + name + "] Client's version of " + version + " does not match the server's version of " +  this.version + ".");
		    out.println("n");
		    out.println(this.version);
		    socket.close();
		    return;
		}
		out.println("y");
		if(!plugin.qc.containsKey(name)) {
		    plugin.qc.put(name, 0);
		}
		plugin.c.add(name);
		plugin.debugger.debug("Connection from " + socket.getInetAddress().getHostName() + ":" + socket.getPort() + " under name " + name + " has been authorised.");
	}

	public void run() {
        if (active) {
            try {
                out.println("heartbeat");
                if (out.checkError()) {
                    plugin.debugger.debug("Connection from " + socket.getInetAddress().getHostName() + ":" + socket.getPort() + " under name " + name + " has disconnected.");
                    plugin.c.remove(name);
                    active = false;
                    return;
                }
                if (in.ready()) {
                    String input = in.readLine();
                    if (!input.equals("heartbeat")) {
                        plugin.debugger.debug("[" + socket.getInetAddress().getHostName() + ":" + socket.getPort() + "] [" + name + "] Received input - " + input);
                        String[] data = input.split(CSS.spacer);
                        if (data[0].equals("player")) {
                            String command = "/" + data[2].replaceAll("\\+", " ");
                            if (data[1].equals("single")) {
                                String name = data[3];
                                Boolean found = false;
                                for (ProxiedPlayer player : plugin.getProxy().getPlayers()) {
                                    if (name.equals(player.getName())) {
                                        player.chat(command);
                                        plugin.debugger.debug("Ran command " + command + " for player " + name + ".");
                                        found = true;
                                        break;
                                    }
                                }
                                if (!found) {
                                    if (plugin.pq.containsKey(name)) {
                                        List<String> commands = plugin.pq.get(name);
                                        commands.add(command);
                                        plugin.pq.put(name, commands);
                                    } else {
                                        plugin.pq.put(name, new ArrayList<String>(Arrays.asList(command)));
                                    }
                                    plugin.debugger.debug(" Since " + name + " is offline the command " + command + " will run when they come online.");
                                }
                            } else if (data[1].equals("all")) {
                                for (ProxiedPlayer player : plugin.getProxy().getPlayers()) {
                                    player.chat(command);
                                }
                                plugin.debugger.debug("Ran command " + command + " for all online players.");
                            }
                        } else {
                            if (data[1].equals("bungee")) {
                                String command = data[2].replaceAll("\\+", " ");
                                plugin.getProxy().getPluginManager().dispatchCommand(plugin.getProxy().getConsole(), command);
                                plugin.debugger.debug("Ran command /" + command + ".");
                            } else {
                                CSS.oq.add(input);
                            }
                        }
                    }
                }
                Integer size = CSS.oq.size();
                Integer count = plugin.qc.get(name);
                if (size > count) {
                    for (int i = count; i < size; i++) {
                        count++;
                        String output = CSS.oq.get(i);
                        String[] data = output.split(CSS.spacer);
                        if (data[1].equals("single")) {
                            if (data[3].equals(name)) {
                                out.println(output);
                                plugin.debugger.debug("[" + socket.getInetAddress().getHostName() + ":" + socket.getPort() + "] [" + name + "] Sent output - " + output);
                            }
                        } else {
                            out.println(output);
                            plugin.debugger.debug("[" + socket.getInetAddress().getHostName() + ":" + socket.getPort() + "] [" + name + "] Sent output - " + output);
                        }
                    }
                    plugin.qc.put(name, count);
                }
            } catch (Exception e) {
                plugin.c.remove(name);
                active = false;
                e.printStackTrace();
            }
        }
    }
}
