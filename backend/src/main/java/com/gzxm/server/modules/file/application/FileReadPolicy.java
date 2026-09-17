package com.gzxm.server.modules.file.application;

import com.gzxm.server.common.security.CurrentUser;

/** Business modules may grant read access to a linked file without exposing their repositories. */
public interface FileReadPolicy {
    boolean canRead(long fileId, CurrentUser user);
}
