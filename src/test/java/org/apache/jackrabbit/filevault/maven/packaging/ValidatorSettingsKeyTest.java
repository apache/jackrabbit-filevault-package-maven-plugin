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
package org.apache.jackrabbit.filevault.maven.packaging;

import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

public class ValidatorSettingsKeyTest {

    @Test
    public void testFromString() {
        Assert.assertEquals(new ValidatorSettingsKey("id1", "id1", false, null, null), ValidatorSettingsKey.fromString("id1"));
        Assert.assertEquals(new ValidatorSettingsKey("id2:subpackage", "id2", true, null, null), ValidatorSettingsKey.fromString("id2:subpackage"));
        Assert.assertThrows(IllegalArgumentException.class, () -> {ValidatorSettingsKey.fromString("id1:invalid");} );
        Assert.assertEquals(new ValidatorSettingsKey("id3:othergroup:myname", "id3", false, "othergroup", "myname"),  ValidatorSettingsKey.fromString("id3:othergroup:myname"));
        Assert.assertEquals(new ValidatorSettingsKey("id4:*:othername", "id4", false, "*", "othername"),  ValidatorSettingsKey.fromString("id4:*:othername"));
    }

    @Test
    public void testComparator() {
        SortedSet<ValidatorSettingsKey> keys = new TreeSet<>();
        
        ValidatorSettingsKey key1 = new ValidatorSettingsKey("id1", "id1", false, null, null);
        ValidatorSettingsKey key2 = new ValidatorSettingsKey("id1:subpackage", "id1", true, null, null);
        ValidatorSettingsKey key3 = new ValidatorSettingsKey("id1:*:mypackage", "id1", false, "*", "mypackage");
        ValidatorSettingsKey key4 = new ValidatorSettingsKey("id1:mygroup:mypackage", "id1", false, "mygroup", "mypackage");
        ValidatorSettingsKey key5 = new ValidatorSettingsKey("id1:mygroup:otherpackage", "id1", false, "mygroup", "otherpackage");
        Assert.assertEquals(-1, Integer.signum(key1.compareTo(key2)));
        Assert.assertEquals(1, Integer.signum(key2.compareTo(key1)));
        Assert.assertEquals(-1, Integer.signum(key2.compareTo(key3)));
        Assert.assertEquals(1, Integer.signum(key3.compareTo(key2)));
        Assert.assertEquals(-1, Integer.signum(key3.compareTo(key4)));
        Assert.assertEquals(1, Integer.signum(key4.compareTo(key3)));
        Assert.assertEquals(-1, Integer.signum(key4.compareTo(key5)));
        Assert.assertEquals(1, Integer.signum(key5.compareTo(key4)));
        Assert.assertEquals(0, Integer.signum(key5.compareTo(key5)));
        keys.add(key5);
        keys.add(key4);
        keys.add(key3);
        keys.add(key2);
        keys.add(key1);
        Assert.assertEquals("id1,id1:subpackage,id1:*:mypackage,id1:mygroup:mypackage,id1:mygroup:otherpackage", keys.stream().map(ValidatorSettingsKey::getKey).collect(Collectors.joining(",")));
    }
}
