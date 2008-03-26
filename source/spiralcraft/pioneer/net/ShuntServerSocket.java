//
// Copyright (c) 1998,2008 Michael Toth
// Spiralcraft Inc., All Rights Reserved
//
// This package is part of the Spiralcraft project and is licensed under
// a multiple-license framework.
//
// You may not use this file except in compliance with the terms found in the
// SPIRALCRAFT-LICENSE.txt file at the top of this distribution, or available
// at http://www.spiralcraft.org/licensing/SPIRALCRAFT-LICENSE.txt.
//
// Unless otherwise agreed to in writing, this software is distributed on an
// "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
//
package spiralcraft.pioneer.net;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import java.util.LinkedList;
import java.util.HashMap;

import java.io.IOException;

public class ShuntServerSocket
{
	private static HashMap _listeners=new HashMap();
	
	private int _port;
	private Object _queueMonitor=new Object();
	private LinkedList _queue=new LinkedList();
	private boolean _closed=false;
	
	private static void addListener(int port,ShuntServerSocket sock)
	{	_listeners.put(new Integer(port),sock);
	}

	private static void removeListener(int port)
	{	_listeners.remove(new Integer(port));
	}
	
	public static void connect(ShuntSocket client)
		throws IOException
	{ 
		ShuntServerSocket sock=(ShuntServerSocket) _listeners.get(new Integer(client.getPort()));
		if (sock!=null)
		{ sock.notifyConnect(client);
		}
		else
		{ throw new SocketException("Connection Failed: Nothing listening on port "+client.getPort()+".");
		}
	}

	/**
	 * 'Listens' on a port for requests to come in
	 *    from ShuntSockets.
	 */
	public ShuntServerSocket(int port)
	{
		_port=port;
		addListener(port,this);
	}
	
	public Socket accept()
	{
		while (!_closed)
		{
			synchronized (_queueMonitor)
			{
				if (_queue.size()>0)
				{ return (ShuntSocket) _queue.removeFirst();
				}
				try
				{	_queueMonitor.wait(1000);
				}
				catch (InterruptedException x)
				{ }
			}
		}
		return null;
	}
	
	public void close()
	{
		removeListener(_port);
		_closed=true;
		synchronized (_queueMonitor)
		{	_queueMonitor.notify();
		}
	}
	
	public void notifyConnect(ShuntSocket client)
		throws IOException
	{
		ShuntSocket sock=new ShuntSocket(client);
		_queue.add(sock);
		synchronized (_queueMonitor)
		{	_queueMonitor.notify();
		}
	}
}
