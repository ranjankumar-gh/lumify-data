package com.altamiracorp.lumify.sql.model.user;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.assertArrayEquals;

@RunWith(JUnit4.class)
public class SqlUserTest {

    @Test
    public void testUserPasswordHash() {
        byte[] passwordHash = { 0x0, 0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8, 0x9, 0xa, 0xb, 0xc, 0xd, 0xe, 0xf };
        SqlUser user = new SqlUser();
        user.setPasswordHash(passwordHash);
        assertArrayEquals(passwordHash, user.getPasswordHash());
    }

    @Test
    public void testUserPasswordSalt() {
        byte[] passwordSalt = { 0x3, 0x2, 0x1, 0x0 };
        SqlUser user = new SqlUser();
        user.setPasswordSalt(passwordSalt);
        assertArrayEquals(passwordSalt, user.getPasswordSalt());
    }
}
