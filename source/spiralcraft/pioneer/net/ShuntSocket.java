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

import java.net.Socket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

public class ShuntSocket
	extends Socket
{

	private int port;
	private ShuntSocket _peer;
	private PipedInputStream _in=new PipedInputStream();
	private PipedOutputStream _out=new PipedOutputStream();

	/**
	 * Create a new client-side shunt socket that contacts a ShuntServerSocket
	 *   'listening' at the specified address.
	 */
	public ShuntSocket(int port)
		throws IOException
	{
		
		this.port=port;
		ShuntServerSocket.connect(this);
	}
	
	
	/**
	 * Create a new server-side shunt socket that is connected to the specified
	 *   client.
	 */
	public ShuntSocket(ShuntSocket client)
		throws IOException
	{ 
		setPeer(client);
		client.setPeer(this);
	}
	
	/**
	 * Called by a server-side shut socket to notify itself of its client and
	 *   a client-side socket of it's peer.
	 */
	public void setPeer(ShuntSocket peer)
		throws IOException
	{
		_peer=peer;
		_in.connect((PipedOutputStream) peer.getOutputStream());
	}
	
	public InetAddress getInetAddress()
	{
	 	try
	 	{	return InetAddress.getLocalHost();
	 	}
	 	catch (UnknownHostException x)
	 	{ }
	 	return null;
	}
	
	public InetAddress getLocalAddress()
	{ return getInetAddress();
	}

	public int getPort()
	{ return port;
	}
	
	public int getLocalPort()
	{ return port;
	}
	
	public InputStream getInputStream()
	{ return _in;
	}	
	
	public OutputStream getOutputStream()
	{ return _out;
	}
	
	public void setSoLinger(int soLinger)
	{
	}
	
	public int getSoLinger()
	{ return 0;
	}
	
	public void setSoTimeout(int soTimeout)
	{
	}

	public int getSoTimeout()
	{ return 0;
	}

	public void setTcpNoDelay(boolean tnd)
	{
	}
	
	public boolean getTcpNoDelay()
	{ return true;
	}
	
	public void close()
		throws IOException
	{
		try
		{	_in.close();
		}
		catch (IOException x)
		{ }

		try
		{	_out.close();
		}
		catch (IOException x)
		{ }
		
		_peer.close();
	}

}
