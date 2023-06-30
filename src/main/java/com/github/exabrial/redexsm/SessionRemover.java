package com.github.exabrial.redexsm;

public interface SessionRemover {

	void evictSession(String sessionId);

	void destroySession(String sessionId);
}
