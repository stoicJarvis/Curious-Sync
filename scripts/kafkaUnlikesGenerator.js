const fs = require('fs');
const path = require('path');
const readline = require('readline');

async function main() {
    const inputFilePath = path.join(__dirname, 'kafka-like-events.jsonl');
    const outputFilePath = path.join(__dirname, 'kafka-unlike-events.jsonl');

    if (!fs.existsSync(inputFilePath)) {
        console.error(`Input file ${inputFilePath} not found. Please run kafkaEventsGenerator.js first.`);
        process.exit(1);
    }

    console.log(`Reading events from ${inputFilePath}...`);
    console.log(`Generating unlikes to ${outputFilePath}...`);

    const fileStream = fs.createReadStream(inputFilePath);
    const rl = readline.createInterface({
        input: fileStream,
        crlfDelay: Infinity
    });

    const writeStream = fs.createWriteStream(outputFilePath);
    let count = 0;
    const start = Date.now();

    for await (const line of rl) {
        if (!line.trim()) continue;

        try {
            const event = JSON.parse(line);
            event.eventType = 'unlike';
            
            if (!writeStream.write(JSON.stringify(event) + '\n')) {
                await new Promise(resolve => writeStream.once('drain', resolve));
            }

            count++;
            if (count > 0 && count % 250000 === 0) {
                console.log(`  Progress: ${count.toLocaleString()} unlikes generated...`);
            }
        } catch (err) {
            console.error(`Failed to parse line: ${line}`, err.message);
        }
    }

    writeStream.end();
    writeStream.on('finish', () => {
        const duration = ((Date.now() - start) / 1000).toFixed(1);
        console.log(`Done! Generated ${count.toLocaleString()} unlike events in ${duration}s`);
    });
}

main();
