const path = require("path");

module.exports = (env, argv) => ({
    mode: env,
    entry: "./src/js/webpack.entry.js",
    output: {
        path: path.resolve(__dirname, "../../target/public/js-out"),
        filename: env == "production" ? "xstate_bundle.min.js" : "xstate_bundle.js",
        library: "XState",
        libraryTarget: "umd"
    }
});
