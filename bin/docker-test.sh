docker run \
    --rm \
    --init \
    -it \
    -p 9202:9202 \
    -e EVA_STOCK_URL='http://192.168.225.3:8080/middleware/pim/stock' \
    -v C:/code/eva-plugin/bin/../build/elasticsearch:/usr/share/elasticsearch/plugins/eva-plugin \
    -v C:/code/eva-plugin/bin/elasticsearch.yml:/usr/share/elasticsearch/config/elasticsearch.yml \
    -v E:/data/elasticsearch/:/usr/share/elasticsearch/data \
    elasticsearch:7.12.1