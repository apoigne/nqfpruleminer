### Structure of the Application

#### ruleminer (App)

- reads Config file
- starts Master actor

#### Master (Actor)

- starts workers (router: Robinround)
- if aggregation is needed
      starts Aggregator (with workrs listing)
- starts reader with either worher or aggregator listening

- combines the root distributions obtained from the workers


#### Reader
 
- reads instances
- skips instances that do not satisfy the instanceFilter Rule
- creates items and derived items
- sends the items as dataframe to the listener

#### Aggregator

- creates a history for every aggregation feature
- aggregates for every obntained dataframe
- adds aggregated items to the dataframe
- sends the dataframe to the woprker

#### Worker

- send root distribution

#### Coding
