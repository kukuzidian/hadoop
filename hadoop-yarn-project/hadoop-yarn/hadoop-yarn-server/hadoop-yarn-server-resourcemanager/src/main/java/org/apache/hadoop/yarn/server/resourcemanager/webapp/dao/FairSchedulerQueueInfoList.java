package org.apache.hadoop.yarn.server.resourcemanager.webapp.dao;

import java.util.ArrayList;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class FairSchedulerQueueInfoList {
  private ArrayList<FairSchedulerQueueInfo> queue;

  public FairSchedulerQueueInfoList() {
    queue = new ArrayList<>();
  }

  public ArrayList<FairSchedulerQueueInfo> getQueueInfoList() {
    return this.queue;
  }

  public boolean addToQueueInfoList(FairSchedulerQueueInfo e) {
    return this.queue.add(e);
  }

  public FairSchedulerQueueInfo getQueueInfo(int i) {
    return this.queue.get(i);
  }
}
