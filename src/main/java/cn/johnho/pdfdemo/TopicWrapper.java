package cn.johnho.pdfdemo;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.xmind.core.ITopic;

@EqualsAndHashCode
@Getter
@Setter
public class TopicWrapper {
    private ITopic topic;

    private TopicWrapper parentTopicWrapper;

    private Integer pageNumber;

    public TopicWrapper(ITopic topic, TopicWrapper parentTopicWrapper, Integer pageNumber) {
        this.topic = topic;
        this.parentTopicWrapper = parentTopicWrapper;
        this.pageNumber = pageNumber;
    }
}
