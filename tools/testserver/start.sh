#!/bin/bash
cd /home/user/testserver
# Garde le tube ouvert pour pouvoir envoyer des commandes console avec : echo "cmd" > console.in
(sleep infinity > console.in) &
exec java -Xms1G -Xmx2G -jar paper.jar nogui < console.in
