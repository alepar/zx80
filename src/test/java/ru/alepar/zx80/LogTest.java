package ru.alepar.zx80;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * User: alepar
 * Date: Sep 14, 2010
 */
public class LogTest {

    @Test
    public void loggingExecutesWithoutExceptions() {
        Logger logger = LoggerFactory.getLogger(LogTest.class);
        logger.info("Hello World");
    }
}
