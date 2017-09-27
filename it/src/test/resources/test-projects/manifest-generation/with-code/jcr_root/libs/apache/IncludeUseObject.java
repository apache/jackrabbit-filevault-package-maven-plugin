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
package libs.apache;

import java.io.IOException;

import javax.servlet.ServletException;

import org.apache.jackrabbit.api.query.JackrabbitQueryResult;
import org.apache.sling.api.resource.Resource;

public class IncludeUseObject {

    private static final String RESOURCE = "includeResource";
    private static final String RESOURCE_TYPE = "includeResourceType";
    private static final String OPTIONS = "includeOptions";
    private static final String SCRIPT = "includeScript";


    protected void activate() {
        // only for testing reference to oaj.api.query package
        String testOnly = JackrabbitQueryResult.class.getName();
        // only for testing reference to oas.api.resource package
        String resource = Resource.class.getName();
    }

    /**
     * Include the provided resource, using the provided resource type and options.
     *
     * @return Output from including the resource
     */
    public String includeResource() throws ServletException, IOException {
        return "";
    }

    /**
     * Include the provided script, using the provided options.
     *
     * @return Output from including the script
     */
    public String includeScript() throws ServletException, IOException {
        return "";
    }
}
