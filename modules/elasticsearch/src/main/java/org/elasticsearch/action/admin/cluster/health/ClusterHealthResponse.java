/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.action.admin.cluster.health;

import com.google.common.collect.Maps;
import org.elasticsearch.action.ActionResponse;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import static org.elasticsearch.action.admin.cluster.health.ClusterIndexHealth.*;

/**
 * @author kimchy (shay.banon)
 */
public class ClusterHealthResponse implements ActionResponse, Iterable<ClusterIndexHealth> {

    private String clusterName;

    int activeShards = 0;

    int relocatingShards = 0;

    int activePrimaryShards = 0;

    boolean timedOut = false;

    ClusterHealthStatus status = ClusterHealthStatus.RED;

    Map<String, ClusterIndexHealth> indices = Maps.newHashMap();

    ClusterHealthResponse() {
    }

    public ClusterHealthResponse(String clusterName) {
        this.clusterName = clusterName;
    }

    public String clusterName() {
        return clusterName;
    }

    public int activeShards() {
        return activeShards;
    }

    public int relocatingShards() {
        return relocatingShards;
    }

    public int activePrimaryShards() {
        return activePrimaryShards;
    }

    /**
     * <tt>true</tt> if the waitForXXX has timeout out and did not match.
     */
    public boolean timedOut() {
        return this.timedOut;
    }

    public ClusterHealthStatus status() {
        return status;
    }

    public Map<String, ClusterIndexHealth> indices() {
        return indices;
    }

    @Override public Iterator<ClusterIndexHealth> iterator() {
        return indices.values().iterator();
    }

    @Override public void readFrom(DataInput in) throws IOException, ClassNotFoundException {
        clusterName = in.readUTF();
        activePrimaryShards = in.readInt();
        activeShards = in.readInt();
        relocatingShards = in.readInt();
        status = ClusterHealthStatus.fromValue(in.readByte());
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            ClusterIndexHealth indexHealth = readClusterIndexHealth(in);
            indices.put(indexHealth.index(), indexHealth);
        }
        timedOut = in.readBoolean();
    }

    @Override public void writeTo(DataOutput out) throws IOException {
        out.writeUTF(clusterName);
        out.writeInt(activePrimaryShards);
        out.writeInt(activeShards);
        out.writeInt(relocatingShards);
        out.writeByte(status.value());
        out.writeInt(indices.size());
        for (ClusterIndexHealth indexHealth : this) {
            indexHealth.writeTo(out);
        }
        out.writeBoolean(timedOut);
    }

}