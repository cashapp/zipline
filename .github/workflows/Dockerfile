# We use Docker because GitHub actions doesn't offer virtual environments for old distros, and we
# need to build on old distros to run on old distros.

FROM amd64/oraclelinux:9
RUN dnf install gcc-c++ cmake3 java-11-openjdk-devel
