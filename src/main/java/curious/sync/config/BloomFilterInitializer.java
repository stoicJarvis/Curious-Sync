package curious.sync.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import curious.sync.services.core.BloomFilterService;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class BloomFilterInitializer implements ApplicationRunner {

    @Autowired
    private BloomFilterService bloomFilterService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("Starting Bloom filter initialization on application startup");
        bloomFilterService.initializeBloomFilter();
        log.info("Bloom filter initialization completed");
    }
}
