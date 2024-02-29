#!/bin/bash

java -Dfile.encoding=UTF-8 -jar ../apollo-configservice/target/apollo-configservice-0.0.1-MTG.jar &
java -Dfile.encoding=UTF-8 -jar ../apollo-adminservice/target/apollo-adminservice-0.0.1-MTG.jar &
java -Dfile.encoding=UTF-8 -jar ../apollo-portal/target/apollo-portal-0.0.1-MTG.jar &

wait  # 等待所有进程完成