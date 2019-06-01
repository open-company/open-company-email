# [OpenCompany](https://github.com/open-company) Email Service

[![AGPL License](http://img.shields.io/badge/license-AGPL-blue.svg?style=flat)](https://www.gnu.org/licenses/agpl-3.0.en.html)
[![Build Status](http://img.shields.io/travis/open-company/open-company-email.svg?style=flat)](https://travis-ci.org/open-company/open-company-email)
[![Dependencies Status](https://versions.deps.co/open-company/open-company-email/status.svg)](https://versions.deps.co/open-company/open-company-email)


## Background

> There is not a crime, there is not a dodge, there is not a trick, there is not a swindle, there is not a vice which does not live by secrecy.

> -- Joseph Pulitzer

Teams struggle to keep everyone on the same page. People are hyper-connected in the moment with chat and email, but it gets noisy as teams grow, and people miss key information. Everyone needs clear and consistent leadership, and the solution is surprisingly simple and effective - **great leadership updates that build transparency and alignment**.

With that in mind we designed [Carrot](https://carrot.io/), a software-as-a-service application powered by the open source [OpenCompany platform](https://github.com/open-company) and a source-available [web UI](https://github.com/open-company/open-company-web).

With Carrot, important company updates, announcements, stories, and strategic plans create focused, topic-based conversations that keep everyone aligned without interruptions. When information is shared transparently, it inspires trust, new ideas and new levels of stakeholder engagement. Carrot makes it easy for leaders to engage with employees, investors, and customers, creating alignment for everyone.

Transparency expectations are changing. Organizations need to change as well if they are going to attract and retain savvy teams, investors and customers. Just as open source changed the way we build software, transparency changes how we build successful companies with information that is open, interactive, and always accessible. Carrot turns transparency into a competitive advantage.

To get started, head to: [Carrot](https://carrot.io/)


## Overview

The OpenCompany Email Service handles composition and delivery of emails for other OpenCompany services.


## Local Setup

Prospective users of [Carrot](https://carrot.io/) should get started by going to [Carrot.io](https://carrot.io/). The following local setup is **for developers** wanting to work on the OpenCompany Email Service.

Most of the dependencies are internal, meaning [Leiningen](https://github.com/technomancy/leiningen) will handle getting them for you. There are a few exceptions:

* [Java](https://openjdk.java.net/) - a Java 12+ JRE is needed to run Clojure
* [Leiningen](https://github.com/technomancy/leiningen) - Leiningen  2.9.1+ is a Clojure build and dependency management tool
* [Node.js](https://nodejs.org/en/) - v8.9.1+ of the JavaScript runtime
* [Juice](https://github.com/Automattic/juice) - Juice 4.2.2+ inlines CSS into HTML

#### Java

Your system may already have Java 12+ installed. You can verify this with:

```console
java -version
```

If you do not have Java 12+ [download it](http://www.oracle.com/technetwork/java/javase/downloads/index.html) and follow the installation instructions.

An option we recommend is [OpenJDK](https://openjdk.java.net/). There are [instructions for Linux](https://openjdk.java.net/install/index.html) and [Homebrew](https://brew.sh/) can be used to install OpenJDK on a Mac with:

```
brew update && brew cask install adoptopenjdk
```

#### Leiningen

Leiningen is easy to install:

1. Download the latest [lein script from the stable branch](https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein).
1. Place it somewhere that's on your $PATH (`env | grep PATH`). `/usr/local/bin` is a good choice if it is on your PATH.
1. Set it to be executable. `chmod 755 /usr/local/bin/lein`
1. Run it: `lein` This will finish the installation.

Then let Leiningen install the rest of the dependencies:

```console
git clone https://github.com/open-company/open-company-email.git
cd open-company-email
lein deps
```

#### Node.js

For Mac OS X, download the latest long-term support (LTS) `.pkg` installer from the [Node.js download page](https://nodejs.org/en/download/). Double click the package to run it.

For Linux, install nodejs with your distribution's preferred package manager, e.g. `sudo apt-get install build-essential nodejs nodejs-legacy npm`.

You can verify your Node.js installation by running:

```console
node -v
npm -v
```

#### Juice

The Node.js package manager, `npm`, can be used to install juice:

```console
npm install -g juice
```

You can verify your juice installation by running:

```console
juice --version
```

#### Required Configuration & Secrets

An [AWS SQS queue](https://aws.amazon.com/sqs/) is used to pass messages from other OpenCompany services to the email service. Setup an SQS Queue and key/secret/endpoint access to the queue using the AWS Web Console or API.

An API key is needed for [Filestack](https://www.filestack.com/) to build URL's that do image processing.

Make sure you update the section in `project.clj` that looks like this to contain your actual JWT and AWS SQS secrets, and your email domain configuration:

```clojure
:dev [:qa {
  :env ^:replace {
        :open-company-auth-passphrase "this_is_a_dev_secret" ; JWT secret
        :aws-access-key-id "CHANGE-ME"
        :aws-secret-access-key "CHANGE-ME"
        :aws-endpoint "us-east-1"
        :aws-sqs-email-queue "https://sqs.REGION.amazonaws.com/CHANGE/ME"
        :email-from-domain "change.me"
        :email-digest-prefix "[Localhost] "
        :email-images-prefix "https://CHANGE-ME.s3.amazonaws.com"
        :filestack-api-key "CHANGE-ME"
        :intro "true"
        :log-level "debug"
  }
```

You can also override these settings with environmental variables in the form of `AWS_ACCESS_KEY_ID`, etc. Use environmental variables to provide production secrets when running in production.

You will also need to subscribe the SQS queue to the storage SNS topic. To do this you will need to go to the AWS console and follow these instruction:

Go to the AWS SQS Console and select the email queue configured above. From the 'Queue Actions' dropdown, select 'Subscribe Queue to SNS Topic'. Select the SNS topic you've configured your Storage Service instance to publish to, and click the 'Subscribe' button.


## Usage

Prospective users of [Carrot](https://carrot.io/) should get started by going to [Carrot.io](https://carrot.io/). The following usage is **for developers** wanting to work on the OpenCompany Email Service.

**Make sure you've updated `project.clj` as described above.**

To start a development email service:

```console
lein start
```

Or to start a production email service:

```console
lein start!
```

To clean all compiled files:

```console
lein clean
```

To create a production build run:

```console
lein build
```


## Testing

Tests are run in continuous integration of the `master` and `mainline` branches on [Travis CI](https://travis-ci.org/open-company/open-company-email):

[![Build Status](http://img.shields.io/travis/open-company/open-company-email.svg?style=flat)](https://travis-ci.org/open-company/open-company-email)

To run the tests locally:

```console
lein test!
```

## Images

Images are kept in [this folder](resources/images/) and synced to the email_images folder of the predefined S3 bucket. Bucket changes based on the environment.


## Participation

Please note that this project is released with a [Contributor Code of Conduct](https://github.com/open-company/open-company-email/blob/mainline/CODE-OF-CONDUCT.md). By participating in this project you agree to abide by its terms.


## License

Distributed under the [GNU Affero General Public License Version 3](https://www.gnu.org/licenses/agpl-3.0.en.html).

Copyright © 2016-2018 OpenCompany, LLC.

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the [GNU Affero General Public License](https://www.gnu.org/licenses/agpl-3.0.en.html) for more details.