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

import com.hazelcast.config.EventJournalConfig;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.config.JetConfig;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.Sources;
import com.hazelcast.jet.pipeline.WindowDefinition;
import com.hazelcast.map.journal.EventJournalMapEvent;
import trades.tradegenerator.Trade;
import trades.tradegenerator.TradeGenerator;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import static com.hazelcast.jet.pipeline.JournalInitialPosition.START_FROM_CURRENT;
import static com.hazelcast.jet.aggregate.AggregateOperations.counting;
import static com.hazelcast.jet.function.DistributedFunctions.alwaysTrue;

/**
 * A simple demonstration of Jet's continuous operators on an infinite stream.
 * <p>
 * The job streams events from an IMap journal. After the job is started,
 * trades are put to the map that simulate an event stream coming from a stock
 * market. The job then computes the number of trades per ticker within a
 * sliding window of a given duration and dumps the results to console.
 */
public class StockExchange {

    private static final String TRADES_MAP_NAME = "trades";
    private static final int SLIDING_WINDOW_LENGTH_MILLIS = 3_000;
    private static final int SLIDE_STEP_MILLIS = 500;
    private static final int TRADES_PER_SEC = 3_000;
    private static final int NUMBER_OF_TICKERS = 10;
    private static final int JOB_DURATION = 15;

    public static void main(String[] args) {
        System.setProperty("hazelcast.logging.type", "log4j");
        JetConfig config = new JetConfig();
        config.getHazelcastConfig().addEventJournalConfig(new EventJournalConfig()
                .setMapName(TRADES_MAP_NAME)
                .setCapacity(TRADES_PER_SEC * 10));
        config.getInstanceConfig().setCooperativeThreadCount(
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2));

        JetInstance jet = Jet.newJetInstance(config);
        Jet.newJetInstance(config);
        try {
            jet.newJob(buildPipeline());
            TradeGenerator.generate(NUMBER_OF_TICKERS, jet.getMap(TRADES_MAP_NAME), TRADES_PER_SEC, JOB_DURATION);
        } finally {
            Jet.shutdownAll();
        }
    }

    private static Pipeline buildPipeline() {
        Pipeline p = Pipeline.create();

        p.drawFrom(Sources.<Trade, Integer, Trade>mapJournal(TRADES_MAP_NAME,
                alwaysTrue(), EventJournalMapEvent::getNewValue, START_FROM_CURRENT))
         .addTimestamps(Trade::getTime, 3000)
         .groupingKey(Trade::getTicker)
         .window(WindowDefinition.sliding(SLIDING_WINDOW_LENGTH_MILLIS, SLIDE_STEP_MILLIS))
         .aggregate(counting(),
                 (winStart, winEnd, key, result) -> String.format("%s %5s %4d", toLocalTime(winEnd), key, result))
         .drainTo(Sinks.logger());

        return p;
    }

    private static LocalTime toLocalTime(long timestamp) {
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalTime();
    }
}
