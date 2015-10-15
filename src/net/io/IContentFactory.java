package net.io;

import net.dipatch.IContent;
import net.dipatch.ISender;
import net.io.http.IHttpSession;

public interface IContentFactory {
	
	String SESSION_ID = "SESSION_ID";
	
	IContent createContent(byte[] data, IHttpSession httpSession);
	
	IContent createContent(byte[] data, ISender sender);

}
