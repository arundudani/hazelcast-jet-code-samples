/*
 * Copyright (c) 2008-2018, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.hazelcast.core.ITopic;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sink;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.Sources;
import com.hazelcast.util.ExceptionUtil;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.function.Consumer;

/**
 * Demonstrates an implementation of a simple custom sink with {@link com.hazelcast.jet.pipeline.SinkBuilder}
 * which publishes items to Hazelcast Topics in the Pipeline API.
 * <p>
 * Books will be read from the directory, filtered the lines which starts
 * with `The` and published them to the Hazelcast ITopic.
 * <p>
 * The example will attach an ITopic message listener which consumes items from the topic
 * that our sink publishes.
 */
public class CustomTopicSink {

    private static final String TOPIC_NAME = "topic";

    private JetInstance jet;

    private static Pipeline buildPipeline() {
        Pipeline p = Pipeline.create();
        p.drawFrom(Sources.files(getBooksPath()))
         .filter(line -> line.startsWith("The "))
         .drainTo(buildTopicSink());
        return p;
    }

    private static Sink<String> buildTopicSink() {
        return Sinks.<ITopic<String>, String>
                builder((jet) -> jet.getHazelcastInstance().getTopic(TOPIC_NAME))
                .onReceiveFn(ITopic::publish)
                .build();
    }

    public static void main(String[] args) {
        System.setProperty("hazelcast.logging.type", "log4j");
        new CustomTopicSink().go();
    }

    /**
     * Creates a Hazelcast Jet cluster, attaches a topic listener and runs the pipeline
     */
    private void go() {
        try {
            System.out.println("Creating Jet instance 1");
            jet = Jet.newJetInstance();

            System.out.println("Creating Jet instance 2");
            Jet.newJetInstance();

            System.out.println("Configure Topic Listener");
            ITopic<String> topic = jet.getHazelcastInstance().getTopic(TOPIC_NAME);
            addListener(topic, e -> System.out.println("Line starts with `The`: " + e));

            System.out.print("\nRunning the pipeline... ");
            Pipeline p = buildPipeline();
            jet.newJob(p).join();
        } finally {
            Jet.shutdownAll();
        }
    }

    /**
     * Returns the path of the books which will feed the pipeline
     */
    private static String getBooksPath() {
        URL resource = CustomTopicSink.class.getResource("books/");
        try {
            return Paths.get(resource.toURI()).toString();
        } catch (URISyntaxException e) {
            ExceptionUtil.rethrow(e);
        }
        return null;
    }

    /**
     * Attaches a listener to {@link ITopic} which passes published items to the specified consumer
     *
     * @param topic    topic instance which the listener will be added
     * @param consumer message consumer that the added items will be passed on.
     */
    private static void addListener(ITopic<String> topic, Consumer<String> consumer) {
        topic.addMessageListener(event -> consumer.accept(event.getMessageObject()));
    }

}
