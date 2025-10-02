My implementation of the Minecraft OneBlock minigame, as a plugin for Paper servers

## Demo available at 217.182.197.103:25589, versions 1.21.7/8
`/is advancestage` **`/optools`** `/hub` `/coopadd` `/coopkick` `/giveskillxp <mining|farming|fishing> amount`

## Installation instructions (somewhat complicated)
- requires MongoDB
- requires WorldEdit or FastAsyncWorldEdit, WorldGuard and VoidEditGenerator plugins
- create your desired hub world, zip it up into `OneBlockHub.zip` and put it in the folder with other worlds (unless you changed it, it's the same as server directory). temporarily, the spawn location is hardcoded but you should change that in `OneBlockPlugin.java`
- create `plugins/OneBlockPlugin/config.yml`:
```yml
mongodb-connection-string: 'mongodb://localhost:27017' # if using pterodactyl, try: 'mongodb://172.18.0.1:27017'
mongodb-database: 'DatabaseNameHere'
```
- download paper 1.21.8
- run it and enjoy modifying the code for your needs

### AI usage disclaimer
During development, I utilized large language models to assist me in programming, mainly regarding code structure and OOP. Everything is manually checked and verified to be working properly.
