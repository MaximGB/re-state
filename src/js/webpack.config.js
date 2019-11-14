const path = require("path");

module.exports = {
    mode: "production",
    entry: "./src/js/webpack.entry.js",
    output: {
        path: path.resolve(__dirname, "../../target/public/js-out"),
        filename: "xstate_bundle.js",
        library: "XState",
        libraryTarget: "umd"
    }
};
