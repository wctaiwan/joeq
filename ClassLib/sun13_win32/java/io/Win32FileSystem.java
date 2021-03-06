// Win32FileSystem.java, created Fri Jan 11 17:09:56 2002 by joewhaley
// Copyright (C) 2001-3 John Whaley <jwhaley@alum.mit.edu>
// Licensed under the terms of the GNU LGPL; see COPYING for details.
package ClassLib.sun13_win32.java.io;

import Memory.HeapAddress;
import Run_Time.SystemInterface;

/**
 * Win32FileSystem
 *
 * @author  John Whaley <jwhaley@alum.mit.edu>
 * @version $Id: Win32FileSystem.java,v 1.9 2003/05/12 10:04:55 joewhaley Exp $
 */
public abstract class Win32FileSystem {

    // gets the current directory on the named drive.
    private static String getDriveDirectory(int i) {
        byte[] b = new byte[256];
        int result = SystemInterface.fs_getdcwd(i, b);
        if (result == 0) throw new InternalError();
        String res = SystemInterface.fromCString(HeapAddress.addressOf(b));
        // skip "C:"
        if (res.charAt(1) == ':') return res.substring(2);
        else return res;
    }

}
