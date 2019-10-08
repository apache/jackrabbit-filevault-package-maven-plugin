/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.filevault.maven.packaging.impl.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.OutputStream;

/**
 * Similar to {@link BufferedInputStream} but allows to access the delegate via {@link #getDelegate()}.
 */
public class EnhancedBufferedOutputStream extends BufferedOutputStream {

    private final OutputStream delegate;
    
    public EnhancedBufferedOutputStream(OutputStream out, int size) {
        super(out, size);
        this.delegate = out;
    }

    public EnhancedBufferedOutputStream(OutputStream out) {
        super(out);
        this.delegate = out;
    }

    public OutputStream getDelegate() {
        return delegate;
    }

    public static OutputStream tryUnwrap(OutputStream output) {
        while (output instanceof EnhancedBufferedOutputStream) {
            output = EnhancedBufferedOutputStream.class.cast(output).getDelegate();
        }
        return output;
    }
}
